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
import java.util.SortedMap
import java.util.TreeMap

/**
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
    private val account: MutableMap<AssetType, SortedMap<LocalDate, MutableList<CapitalAmount>>> = HashMap()

    val balances: SortedMap<Int, MutableMap<AssetType, BigDecimal>> = TreeMap()

    private var lastDate: ZonedDateTime = Instant.ofEpochMilli(0).atZone(ZoneId.of("UTC"))

    /**
     * Register a trade operation in the account.
     */
    override fun register(op: TradeLog) {
        verifySequenceConsistency(op)

        if (AssetType.AUD == op.incomingAsset) { //We sold a coin for fiat
            processSell(op)
        } else {    //We bought a coin, add to the balance for the purchase date
            registerIncoming(op)
            if (AssetType.AUD != op.outgoingAsset) {    //The coin was bought for another coin, which was sold
                processSell(op)
            }
        }
    }

    private fun processSell(op: TradeLog) {
        //The balance of the sold coin at the time of the trading event
        val soldBalance = account[op.outgoingAsset] ?: throw TradeOperationLogException("No balance to sell $op", op)

        //We sold a coin, subtract from the total balance for the financial year of the sale
        val taxYear = getTaxYear(op.timestamp)
        val balance = getYearlyBalances(taxYear)
            .merge(op.outgoingAsset, op.outgoingAmount, BigDecimal::subtract)!!
        //If -ea is not set, then the program continues, but consistency is questionable
        //Tests on real life data will tell if this situation is possible
        assert(verifyBalance(balance, op)) { "Balance is negative ($balance) after $op" }

        var opAmount = op.outgoingAmount
        var capital = BigDecimal.ZERO
        var opCapital = op.capital
        val iterator = soldBalance.entries.iterator()

        while (iterator.hasNext() && opAmount != BigDecimal.ZERO) {
            val (balanceDate, balancesOnDate) = iterator.next()
            val dailyBalancesIter = balancesOnDate.listIterator()

            while (dailyBalancesIter.hasNext() && opAmount != BigDecimal.ZERO) {
                var iterationCapital: BigDecimal
                var iterationGain: BigDecimal
                val bucket = dailyBalancesIter.next()

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
                    opAmount = opLeftover.negate()

                    //The whole bucket balance is spent on the operation
                    dailyBalancesIter.remove()

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

                //Delete the date if there is no balance left on it
                if (balancesOnDate.isEmpty()) iterator.remove()

                when {
                    iterationGain.signum() < 0 -> {
                        loss.computeIfAbsent(taxYear) { HashMap() }
                            .merge(op.outgoingAsset, iterationGain, BigDecimal::add)

                        lossTotal.merge(taxYear, iterationGain, BigDecimal::add)
                    }

                    //The purchase date is more than a year before the sale date
                    balanceDate.isBefore(op.date.minusYears(1)) -> {
                        gainDiscounted.computeIfAbsent(taxYear) { HashMap() }
                            .merge(op.outgoingAsset, iterationGain, BigDecimal::add)

                        gainDiscountedTotal.merge(taxYear, iterationGain, BigDecimal::add)
                    }

                    else -> {
                        gain.computeIfAbsent(taxYear) { HashMap() }
                            .merge(op.outgoingAsset, iterationGain, BigDecimal::add)

                        gainTotal.merge(taxYear, iterationGain, BigDecimal::add)
                    }
                }
            }

        }

        //Verify that the operation has zero-sum
        val error = capital - op.capital
        if (error.abs() > ERROR_THRESHOLD) {
            logger.error { "Capital error: $error, after selling ${op.outgoingAsset} for ${op.incomingAsset}" }
        }
    }

    /**
     * Register a "receive" transfer operation in the account
     */
    override fun register(op: ReceiveLog) {
        verifySequenceConsistency(op)
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
        verifySequenceConsistency(op)

        val sentBalance = account[op.outgoingAsset] ?: throw TradeOperationLogException("No balance to send $op", op)

        //We sent a coin, subtract from the total balance for the financial year of the sending event
        val balance = getYearlyBalances(getTaxYear(op.timestamp))
            .merge(op.outgoingAsset, op.amount, BigDecimal::subtract)!!
        assert(verifyBalance(balance, op)) { "Balance is negative after $op" }

        var opAmount = op.amount
        val iterator = sentBalance.entries.iterator()

        while (iterator.hasNext() && opAmount != BigDecimal.ZERO) {
            val (_, balancesOnDate) = iterator.next()
            val dailyBalancesIter = balancesOnDate.listIterator()

            while (dailyBalancesIter.hasNext() && opAmount != BigDecimal.ZERO) {
                val bucket = dailyBalancesIter.next()

                val opLeftover = bucket.amount - opAmount

                if (opLeftover.signum() <= 0) {    //It means that the bucket was not enough (or exactly enough)
                    //The leftover from the operation after sending the bucket,
                    //the rest must be taken from the next buckets
                    opAmount = opLeftover.negate()

                    //The whole bucket balance is spent on the operation
                    dailyBalancesIter.remove()
                } else {    //The bucket covered the whole operation
                    bucket.capital = (opLeftover * bucket.capital) / bucket.amount
                    bucket.amount = opLeftover
                    opAmount = BigDecimal.ZERO
                }
            }
            //Delete the date if there is no balance left on it
            if (balancesOnDate.isEmpty()) iterator.remove()
        }
    }

    private fun registerIncoming(op: IncomingLog) {
        val balance = getCoinBalance(op.incomingAsset)
        balance.computeIfAbsent(op.date) { k -> ArrayList() }.add(CapitalAmount(op.capital, op.incomingAmount))
        getYearlyBalances(getTaxYear(op.timestamp)).merge(op.incomingAsset, op.incomingAmount, BigDecimal::add)
    }

    private fun getCoinBalance(coin: AssetType): SortedMap<LocalDate, MutableList<CapitalAmount>> =
        account.computeIfAbsent(coin) { k -> TreeMap() }

    private fun verifySequenceConsistency(op: OrderedLog) {
        if (op.timestamp.isBefore(lastDate)) {
            throw TradeOperationLogException(
                "Trade operation $op is out of order. Last operation timestamp $lastDate",
                op
            )
        }
        lastDate = op.timestamp
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

    private data class CapitalAmount(var capital: BigDecimal, var amount: BigDecimal)

}