package dym.coins.tax.domain

import dym.coins.enum.Coins
import dym.coins.exceptions.TradeOperationLogException
import dym.coins.tax.dto.LogOperation
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.util.SortedMap
import java.util.TreeMap

private const val DEFAULT_TIMEZONE = "Australia/Sydney"

/**
 * @author dym
 * Date: 19.09.2023
 */
class Account(private val timeZone: ZoneId? = ZoneId.of(DEFAULT_TIMEZONE)) {

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

    fun registerTradeOperation(op: LogOperation) {
        val balance = account.computeIfAbsent(op.buy) { k -> TreeMap() }

        if (Coins.AUD.name == op.buy.uppercase()) { //We sold a coin for fiat
            processSell(op)
        } else {    //We bought a coin, add to the balance for the purchase date
            balance.computeIfAbsent(op.date) { k -> ArrayList() }.add(CapitalAmount(op.capital, op.buyAmount))
            getYearlyBalances(op.timestamp.year).merge(op.buy, op.buyAmount, BigDecimal::add)
            if (Coins.AUD.name != op.sell.uppercase()) {    //The coin was bought for another coin, which was sold
                processSell(op)
            }
        }
    }

    internal fun getYearlyBalances(year: Int): MutableMap<String, BigDecimal> {
        //First operation in a new year finalises balances for the previous year, copying them over
        return balances.getOrPut(year) {
            if (balances.isEmpty()) HashMap()
            else HashMap(getYearlyBalances(year - 1)) }
    }

    private fun processSell(op: LogOperation) {
        //We sold a coin, subtract from the total balance for the year of the sale
        val balance = getYearlyBalances(op.timestamp.year).merge(op.sell, op.sellAmount, BigDecimal::subtract)

        verifyBalance(balance, op)

        //The balance of the sold coin at the time of the trading event
        val soldBalance = account[op.sell] ?:
            throw TradeOperationLogException("No balance to sell ${op.sell} for ${op.buy}", op)

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
                    iterationCapital = if (opLeftover.signum() == 0) opCapital else (bucket.amount * opCapital)/opAmount

                    //The leftover from the operation capital after selling the bucket,
                    //the rest will be used for the next buckets
                    opCapital -= iterationCapital

                    iterationGain = iterationCapital - bucket.capital

                    assert(opCapital.signum() >= 0) { "Operation capital cannot be negative" }

                    //The leftover from the operation after selling the bucket,
                    //the rest must be taken from the next buckets
                    opAmount = opLeftover.negate()

                    //The whole balance is spent on the operation
                    dailyBalancesIter.remove()

                    //For verification purposes
                    capital += iterationCapital
                } else {    //The bucket covered the whole operation
                    val bucketUsedCapital = (opAmount * bucket.capital)/bucket.amount
                    bucket.capital -= bucketUsedCapital
                    bucket.amount = opLeftover
                    iterationGain = opCapital - bucketUsedCapital
                    opAmount = BigDecimal.ZERO

                    //For verification purposes
                    capital += opCapital
                }

                val taxYear: Int = getTaxYear(op.date)
                when {
                    iterationGain.signum() < 0
                        -> loss.merge(taxYear, iterationGain, BigDecimal::add)
                    //The purchase date is a year before the sale date
                    balanceDate.isBefore(op.date.minusYears(1))
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
     * Verify that the balance is not negative. If it is, log an error.
     * Balance may become nagative if the log is incomplete, e.g. if the log does not contain all the trades and transfers
     * @return true if the balance is valid, false otherwise
     */
    private fun verifyBalance(balance: BigDecimal?, op: LogOperation): Boolean {
        return if (balance!!.signum() >= 0) true
        else {
            errorLog += ErrorLog(
                String.format(
                    "Negative balance %1f, after operation: buy %2s, %3f, sell %4s, %5f%n",
                    balance, op.buy, op.buyAmount, op.sell, op.sellAmount
                ), op
            )
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

    @JvmRecord
    data class ErrorLog(val error: String, val op: LogOperation)
}