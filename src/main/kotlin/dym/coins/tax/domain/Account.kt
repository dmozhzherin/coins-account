package dym.coins.tax.domain

import dym.coins.exceptions.TradeOperationLogException
import dym.coins.tax.Config.Companion.ERROR_THRESHOLD
import dym.coins.tax.dto.IncomingLog
import dym.coins.tax.dto.OrderedLog
import dym.coins.tax.dto.ReceiveLog
import dym.coins.tax.dto.SendLog
import dym.coins.tax.dto.TradeLog
import mu.KotlinLogging
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.PriorityQueue
import java.util.SortedMap
import java.util.TreeMap

/**
 * Balance tracking and tax calculation using FIFO method for all assets.
 * The implementation is not thread-safe. All operations must be registered in chronological order.
 * Parallel processing is challenging, because swap operations (which are regular buy/sell in Coinspot API)
 * require that all operations involving the asset being sold and preceding the swap operation are processed first.
 * Also, all operations in a financial year must be processed before the first operation in the next financial year.
 * Maybe, multithreading support will be implemented in the future :-)
 *
 * @author dym
 * Date: 19.09.2023
 */
class Account() : Registry {

    private val logger = KotlinLogging.logger {}

    /**
     * Income on coins held for less than a year
     */
    val gain: MutableMap<Int, MutableMap<AssetType, BigDecimal>> = HashMap()

    /**
     * Total income on coins held for less than a year
     */
    val gainTotal: MutableMap<Int, BigDecimal> = HashMap()

    /**
     * Income on coins held for more than a year
     */
    val gainDiscounted: MutableMap<Int, MutableMap<AssetType, BigDecimal>> = HashMap()

    /**
     * Total income on coins held for more than a year
     */
    val gainDiscountedTotal: MutableMap<Int, BigDecimal> = HashMap()

    /**
     * Loss on coins
     */
    val loss: MutableMap<Int, MutableMap<AssetType, BigDecimal>> = HashMap()

    /**
     * Total loss on coins
     */
    val lossTotal: MutableMap<Int, BigDecimal> = HashMap()

    /**
     * Map of coin -> date -> list of buy buckets with corresponding rates
     */
    private val account: MutableMap<AssetType, PriorityQueue<AssetBucket>> = HashMap()

    val balances: SortedMap<Int, MutableMap<AssetType, BigDecimal>> = TreeMap()

    private var lastDate: ZonedDateTime = Instant.ofEpochMilli(0).atZone(ZoneId.of("UTC"))

    private var taxYear = 0

    /**
     * Register a trade operation in the account.
     */
    override fun register(op: TradeLog) {
        ensureSequenceConsistency(op)

        if (AssetType.AUD == op.incomingAsset) { //We sold a coin for fiat
            processSell(op)
        } else {    //We bought a coin, add to the balance for the purchase date
            registerIncoming(op)
            if (AssetType.AUD != op.outgoingAsset) {    //The coin was bought for another coin, which was sold
                processSell(op)
            }
        }
    }

    /**
     * Register a "receive" transfer operation in the account
     */
    override fun register(op: ReceiveLog) {
        ensureSequenceConsistency(op)
        registerIncoming(op)
    }

    /**
     * Register a send transfer operation in the account.
     * Sending is like selling, but gain/loss is not calculated.
     *
     * To be precise, any disposal of an asset is a taxable event, unless sending to self.
     * Since the crypto exchange does not know the purpose of the transfer, they don't even try
     * to calculate taxes. As I know exactly that I am only sending to myself,
     * I can ignore the taxable event as long as it is my toy project.
     */
    override fun register(op: SendLog) {
        ensureSequenceConsistency(op)

        val sentBalance = account[op.outgoingAsset] ?: throw TradeOperationLogException("No balance to send $op", op)

        //We sent a coin, subtract from the total balance for the financial year of the sending event
        val balance = getYearlyBalances(taxYear)
            .merge(op.outgoingAsset, op.amount, BigDecimal::subtract)!!
        assert(verifyBalance(balance, op)) { "Balance is negative after $op" }

        var opAmount = op.amount

        while (sentBalance.isNotEmpty() && opAmount != BigDecimal.ZERO) {
            val bucket = sentBalance.peek()

            val opLeftover = bucket.amount - opAmount

            if (opLeftover.signum() <= 0) {    //It means that the bucket was not enough (or exactly enough)
                //The leftover from the operation after sending the bucket,
                //the rest must be taken from the next buckets
                opAmount = opLeftover.abs()

                //The whole bucket balance is spent on the operation
                sentBalance.remove()
            } else {    //The bucket covered the whole operation
                bucket.capital = (opLeftover * bucket.capital) / bucket.amount
                bucket.amount = opLeftover
                opAmount = BigDecimal.ZERO
            }
        }

        if (opAmount > ERROR_THRESHOLD) {
            throw TradeOperationLogException("Insufficient ($opAmount) balance to send $op", op)
        }
    }


    private fun processSell(op: TradeLog) {
        //The balance of the sold coin at the time of the trading event
        val soldBalance = account[op.outgoingAsset] ?: throw TradeOperationLogException("No balance to sell $op", op)

        //We sold a coin, subtract from the total balance for the financial year of the sale
        val balance = getYearlyBalances(taxYear)
            .merge(op.outgoingAsset, op.outgoingAmount, BigDecimal::subtract)!!
        //If -ea is not set, then the program continues, but consistency is questionable
        //Tests on real life data will tell if this situation is possible
        assert(verifyBalance(balance, op)) { "Balance is negative ($balance) after $op" }

        var opAmount = op.outgoingAmount
        var capital = BigDecimal.ZERO
        var opCapital = op.capital

        while (soldBalance.isNotEmpty() && opAmount != BigDecimal.ZERO) {
            val bucket = soldBalance.peek()
            var iterationCapital: BigDecimal
            var iterationGain: BigDecimal

            val opLeftover = bucket.amount - opAmount

            if (opLeftover.signum() <= 0) {    //It means that the bucket was not enough (or exactly enough)
                iterationCapital =
                    if (opLeftover.signum() == 0) opCapital else (bucket.amount * opCapital) / opAmount

                //The leftover from the operation capital after selling the bucket,
                //the rest will be used for more buckets
                opCapital -= iterationCapital

                iterationGain = iterationCapital - bucket.capital

                assert(opCapital.signum() >= 0) { "Operation capital cannot be negative $op" }

                //The leftover from the operation after selling the bucket,
                //the rest must be taken from the next buckets
                opAmount = opLeftover.abs()

                //The whole bucket balance is spent on the operation
                soldBalance.remove()

                //For verification purposes
                capital += iterationCapital
            } else {    //The bucket covered the whole operation
                val usedBucketCapital = (opAmount * bucket.capital) / bucket.amount
                bucket.capital -= usedBucketCapital
                bucket.amount = opLeftover
                iterationGain = opCapital - usedBucketCapital
                opAmount = BigDecimal.ZERO

                //For verification purposes
                capital += opCapital
            }

            when {
                iterationGain.signum() < 0 -> registerLoss(op.outgoingAsset, iterationGain)

                //The purchase date is more than a year before the sale date
                bucket.date.isBefore(op.date.minusYears(1)) ->
                    registeredGainDiscounted(op.outgoingAsset, iterationGain)

                else -> registerGain(op.outgoingAsset, iterationGain)
            }

        }

        if (opAmount > ERROR_THRESHOLD) {
            throw TradeOperationLogException("Insufficient ($opAmount) balance to sell $op", op)
        }

        //Verify that the operation has zero-sum
        val error = capital - op.capital
        if (error.abs() > ERROR_THRESHOLD) {
            logger.error { "Capital error: $error, after selling ${op.outgoingAsset} for ${op.incomingAsset}" }
        }
    }

    private fun registerGain(asset: AssetType, iterationGain: BigDecimal) {
        gain.computeIfAbsent(taxYear) { HashMap() }
            .merge(asset, iterationGain, BigDecimal::add)

        gainTotal.merge(taxYear, iterationGain, BigDecimal::add)
    }

    private fun registeredGainDiscounted(asset: AssetType, iterationGain: BigDecimal) {
        gainDiscounted.computeIfAbsent(taxYear) { HashMap() }
            .merge(asset, iterationGain, BigDecimal::add)

        gainDiscountedTotal.merge(taxYear, iterationGain, BigDecimal::add)
    }

    private fun registerLoss(asset: AssetType, iterationGain: BigDecimal) {
        loss.computeIfAbsent(taxYear) { HashMap() }
            .merge(asset, iterationGain, BigDecimal::add)

        lossTotal.merge(taxYear, iterationGain, BigDecimal::add)
    }


    private fun registerIncoming(op: IncomingLog) {
        getCoinBalance(op.incomingAsset).add(AssetBucket.of(op))
        getYearlyBalances(taxYear).merge(op.incomingAsset, op.incomingAmount, BigDecimal::add)
    }

    private fun getCoinBalance(assetType: AssetType): PriorityQueue<AssetBucket> =
        account.computeIfAbsent(assetType) { k -> PriorityQueue(AssetBucket.comparatorFIFO) }

    private fun ensureSequenceConsistency(op: OrderedLog) {
        if (op.timestamp.isBefore(lastDate)) {
            throw TradeOperationLogException(
                "Trade operation $op is out of order. Last operation timestamp $lastDate", op
            )
        }
        lastDate = op.timestamp
        taxYear = getTaxYear(op.timestamp)
    }

    internal fun getYearlyBalances(year: Int): MutableMap<AssetType, BigDecimal> {
        //First operation in a new financial year finalises balances for the previous year, copying them over
        return balances.getOrPut(year) {
            if (balances.isEmpty()) HashMap()
            else HashMap(getYearlyBalances(year - 1))
        }
    }

    /**
     * Verify that the balance is not negative. If it is, log an error.
     * Balance may become negative if the log is incomplete, e.g. if the log does not contain all the trades and transfers,
     * or due to rounding errors.
     * @return true if the balance is valid, false otherwise
     */
    private fun verifyBalance(balance: BigDecimal, op: OrderedLog): Boolean {
        return if (balance.signum() >= 0) true
        else {
            val message = "Negative balance $balance, after operation: $op"
            if (balance.abs() < ERROR_THRESHOLD) {
                logger.warn(message)
                true
            } else {
                logger.error(message)
                false
            }
        }
    }

    /**
     * Get the tax year for the given date. In Australia financial year starts on 1 July.
     */
    private fun getTaxYear(date: ZonedDateTime) = if (date.month < Month.JULY) date.year else date.year + 1

    private data class AssetBucket(var capital: BigDecimal, var amount: BigDecimal, val date: LocalDate) {
        val rate: BigDecimal = capital / amount

        companion object {
            fun of(incomingLog: IncomingLog) =
                AssetBucket(incomingLog.capital, incomingLog.incomingAmount, incomingLog.date)

            val comparatorFIFO = Comparator.comparing(AssetBucket::date)
            val comparatorLIFO = Comparator.comparing(AssetBucket::date).reversed()
            val comparatorHIFO = Comparator.comparing(AssetBucket::rate).reversed()
        }
    }

}