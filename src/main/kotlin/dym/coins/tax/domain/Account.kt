package dym.coins.tax.domain

import dym.coins.enum.Coins
import dym.coins.exceptions.TradeOperationLogException
import dym.coins.tax.dto.IncomingLog
import dym.coins.tax.dto.OrderedLog
import dym.coins.tax.dto.ReceiveLog
import dym.coins.tax.dto.SendLog
import dym.coins.tax.dto.TradeLog
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
class Account() {

    /**
     * Income on coins held for less than a year
     */
    val gain: MutableMap<Int, BigDecimal> = HashMap()

    /**
     * Income on coins held for more than a year
     */
    val gainDiscounted: MutableMap<Int, BigDecimal> = HashMap()

    /**
     * Loss on coins
     */
    val loss: MutableMap<Int, BigDecimal> = HashMap()

    /**
     * Map of coin -> date -> list of buy buckets with corresponding rates
     */
    private val account: MutableMap<String, SortedMap<LocalDate, MutableList<CapitalAmount>>> = HashMap()

    val balances: SortedMap<Int, MutableMap<String, BigDecimal>> = TreeMap()

    val errorLog: MutableList<ErrorLog> = ArrayList()

    private var lastDate: ZonedDateTime = Instant.ofEpochMilli(0).atZone(ZoneId.of("UTC"))

    fun registerTradeOperation(op: TradeLog) {
        verifySequenceConsistency(op)

        if (Coins.AUD.name == op.buy.uppercase()) { //We sold a coin for fiat
            processSellFiFo(op)
        } else {    //We bought a coin, add to the balance for the purchase date
            registerIncoming(op)
            if (Coins.AUD.name != op.sell.uppercase()) {    //The coin was bought for another coin, which was sold
                processSellFiFo(op)
            }
        }
    }

    private fun processSellFiFo(op: TradeLog) {
        //The balance of the sold coin at the time of the trading event
        val soldBalance = account[op.sell] ?: throw TradeOperationLogException("No balance to sell $op", op)

        //We sold a coin, subtract from the total balance for the year of the sale
        val balance = getYearlyBalances(op.timestamp.year).merge(op.sell, op.sellAmount, BigDecimal::subtract)
        assert(verifyBalance(balance, op)) { "Balance is negative after $op" }

        var opAmount = op.sellAmount
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
                    //the rest will be used for the next buckets
                    opCapital -= iterationCapital

                    iterationGain = iterationCapital - bucket.capital

                    assert(opCapital.signum() >= 0) { "Operation capital cannot be negative" }

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

                val taxYear: Int = getTaxYear(op.date())
                when {
                    iterationGain.signum() < 0
                    -> loss.merge(taxYear, iterationGain, BigDecimal::add)
                    //The purchase date is a year before the sale date
                    balanceDate.isBefore(op.date().minusYears(1))
                    -> gainDiscounted.merge(taxYear, iterationGain, BigDecimal::add)

                    else -> gain.merge(taxYear, iterationGain, BigDecimal::add)
                }
            }
            //Delete the date if there is no balance left on it
            if (balancesOnDate.isEmpty()) iterator.remove()
        }

        //Verify that the operation has zero-sum
        val error = capital - op.capital
        if (error.abs() > EPSILON) {
            errorLog += ErrorLog(
                String.format("Capital error: %1g, after operation selling %2s for %3s", error, op.sell, op.buy), op
            )
        }
    }

    /**
     * Register a "receive" transfer operation in the account
     */
    fun processReceive(op: ReceiveLog) {
        verifySequenceConsistency(op)
        registerIncoming(op);
    }

    /**
     * Register a send transfer operation in the account.
     * Sending is like selling, but gain/loss is not acquired.
     */
    fun processSendFiFo(op: SendLog) {
        verifySequenceConsistency(op)

        val sentBalance = account[op.coin] ?: throw TradeOperationLogException("No balance to send $op", op)

        //We sent a coin, subtract from the total balance for the year of the sending event
        val balance = getYearlyBalances(op.timestamp.year).merge(op.coin, op.amount, BigDecimal::subtract)
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
        val balance = getCoinBalance(op.incomingCoin())
        balance.computeIfAbsent(op.date()) { k -> ArrayList() }.add(CapitalAmount(op.capital, op.incomingAmount()))
        getYearlyBalances(op.timestamp.year).merge(op.incomingCoin(), op.incomingAmount(), BigDecimal::add)
    }

    private fun getCoinBalance(coin: String): SortedMap<LocalDate, MutableList<CapitalAmount>>
        = account.computeIfAbsent(coin) { k -> TreeMap() }

    private fun verifySequenceConsistency(op: OrderedLog) {
        if (op.timestamp.isBefore(lastDate)) {
            throw TradeOperationLogException("Trade operation is out of order $op", op)
        }
        lastDate = op.timestamp
    }

    internal fun getYearlyBalances(year: Int): MutableMap<String, BigDecimal> {
        //First operation in a new year finalises balances for the previous year, copying them over
        return balances.getOrPut(year) {
            if (balances.isEmpty()) HashMap()
            else HashMap(getYearlyBalances(year - 1))
        }
    }

    /**
     * Verify that the balance is not negative. If it is, log an error.
     * Balance may become nagative if the log is incomplete, e.g. if the log does not contain all the trades and transfers
     * @return true if the balance is valid, false otherwise
     */
    private fun verifyBalance(balance: BigDecimal?, op: OrderedLog): Boolean {
        return if (balance!!.signum() >= 0) true
        else {
            errorLog += ErrorLog("Negative balance $balance, after operation: $op", op)
            false
        }
    }

    private fun getTaxYear(date: LocalDate): Int {
        return if (date.month < Month.JULY) date.year else date.year + 1
    }

    private companion object {
        val EPSILON = BigDecimal("1e-8")
    }

    private data class CapitalAmount(var capital: BigDecimal, var amount: BigDecimal)

    /**
     * Log of errors that occurred during the processing of the trade operations
     *
     * @consider adding error type/code
     *
     * @param error error message
     * @param op operation that caused the error
     */
    @JvmRecord
    data class ErrorLog(val error: String, val op: OrderedLog)
}