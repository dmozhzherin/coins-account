package dym.coins.tax.domain

import dym.coins.exceptions.TradeOperationLogException
import dym.coins.tax.dto.ReceiveLog
import dym.coins.tax.dto.SendLog
import dym.coins.tax.dto.TradeLog
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

import java.math.BigDecimal
import java.math.BigDecimal.ONE
import java.math.BigDecimal.ZERO
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * @author dym
 * Date: 23.09.2023
 */
class AccountTest {

    @Test
    fun registerTradeOperation_ensureOrderConsistency() {
        val account = Account()

        account.registerTradeOperation(
            TradeLog(
                ZonedDateTime.parse("2021-10-10T10:00:00+10:00[Australia/Sydney]"),
                "SMTH", ONE, "AUD", ONE, ONE, ONE, ONE
            )
        )

        //Given operation date is after the last operation when registering the operation expect no exception
        account.registerTradeOperation(
            TradeLog(
                ZonedDateTime.parse("2021-10-20T10:00:00+10:00[Australia/Sydney]"),
                "SMTH", ONE, "AUD", ONE, ONE, ONE, ONE
            )
        )

        //Given operation date is before the last operation when registering the operation expect TradeOperationLogException
        assertThrows(TradeOperationLogException::class.java) {
            account.registerTradeOperation(
                TradeLog(
                    ZonedDateTime.parse("2021-10-05T10:00:00+10:00[Australia/Sydney]"),
                    "SMTH", ONE, "AUD", ONE, ONE, ONE, ONE
                )
            )
        }
    }

    @Test
    fun registerTradeOperation_ensureNegativeBalanceVerification() {
        val account = Account()

        account.registerTradeOperation(
            TradeLog.of(
                "2021-10-10T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "5", "AUD", "10", "2", "0.1", "10"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.gain.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertEquals(1, account.balances.size)
        assertBDEquals("5".toBigDecimal(), account.balances[2021]?.get("SMTH"))

        //Given a log operation with correct date, SMTH coin as sell, ANY coin as buy,
        //sellAmount greater than the current balance, any rate, any fee, any capital
        //When registering the operation
        //Then expect assertion error
        assertThrows(AssertionError::class.java) {
            account.registerTradeOperation(
                TradeLog.of(
                    "2021-10-10T10:10:00+10:00[Australia/Sydney]",
                    "ANY", "5", "SMTH", "10", "2", "0.1", "10"
                )
            )
        }

        //Assertion error is fatal, so consistency is broken,
        //but if -ea is not set, then the error is logged and the balances are updated
        assertBDEquals("-5".toBigDecimal(), account.balances[2021]?.get("SMTH"))
        assertFalse(account.errorLog.isEmpty())

        //assert nothing else changed
        assertFalse(account.balances.isEmpty())
        assertTrue(account.gain.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertEquals(1, account.balances.size)
    }

    @Test
    fun registerTradeOperation_buyAndSellWithinAYear() {
        val account = Account()

        account.registerTradeOperation(
            TradeLog.of(
                "2021-10-10T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "5", "AUD", "10", "2", "0.1", "10"
            )
        )

        account.registerTradeOperation(
            TradeLog.of(
                "2021-10-10T10:10:00+10:00[Australia/Sydney]",
                "SMTH", "5", "AUD", "5", "1", "0.1", "5"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.gain.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertEquals(1, account.balances.size)
        assertEquals(1, account.balances[2021]?.size)
        assertBDEquals("10".toBigDecimal(), account.balances[2021]?.get("SMTH"))

        account.registerTradeOperation(
            TradeLog.of(
                "2021-10-10T10:20:00+10:00[Australia/Sydney]",
                "ANY", "8", "AUD", "8", "1", "0.1", "5"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.gain.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertEquals(1, account.balances.size)
        assertEquals(2, account.balances[2021]?.size)
        assertBDEquals("10".toBigDecimal(), account.balances[2021]?.get("SMTH"))
        assertBDEquals("8".toBigDecimal(), account.balances[2021]?.get("ANY"))

        //Given a log operation with correct date, SMTH coin as sell, AUD as buy,
        //sellAmount less than the current SMTH balance, any rate, any fee, any capital
        //When registering the operation
        //Then the operation is registered, balances are updated, no errors are logged and gain is recorded
        account.registerTradeOperation(
            TradeLog.of(
                "2021-10-10T10:30:00+10:00[Australia/Sydney]",
                "AUD", "15", "SMTH", "5", "3", "0.1", "15"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertEquals(1, account.balances.size)
        assertEquals(2, account.balances[2021]?.size)
        assertBDEquals("5".toBigDecimal(), account.balances[2021]?.get("SMTH"))
        assertBDEquals("8".toBigDecimal(), account.balances[2021]?.get("ANY"))
        assertBDEquals("5".toBigDecimal(), account.gain[2022])
    }

    @Test
    fun registerTradeOperation_buyAndSellAfterAYear() {
        val account = Account()

        account.registerTradeOperation(
            TradeLog.of(
                "2021-10-10T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "5", "AUD", "10", "2", "0.1", "10"
            )
        )

        account.registerTradeOperation(
            TradeLog.of(
                "2021-11-11T10:10:00+10:00[Australia/Sydney]",
                "SMTH", "5", "AUD", "5", "1", "0.1", "5"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.gain.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertEquals(1, account.balances.size)
        assertEquals(1, account.balances[2021]?.size)
        assertBDEquals("10".toBigDecimal(), account.balances[2021]?.get("SMTH"))

        account.registerTradeOperation(
            TradeLog.of(
                "2021-12-12T10:20:00+10:00[Australia/Sydney]",
                "ANY", "8", "AUD", "5", "0.625", "0.1", "5"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.gain.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertEquals(1, account.balances.size)
        assertEquals(2, account.balances[2021]?.size)
        assertBDEquals("10".toBigDecimal(), account.balances[2021]?.get("SMTH"))
        assertBDEquals("8".toBigDecimal(), account.balances[2021]?.get("ANY"))

        //Given a log operation with correct date, SMTH coin as sell, AUD as buy,
        //sellAmount less than the current SMTH balance, any rate, any fee, any capital
        //When registering the operation
        //Then the operation is registered, balances are updated, no errors are logged and gain is recorded
        account.registerTradeOperation(
            TradeLog.of(
                "2022-10-11T10:30:00+10:00[Australia/Sydney]",
                "AUD", "21", "SMTH", "7", "3", "0.1", "21"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertEquals(2, account.balances.size)  //balances for 2021 and 2022
        assertEquals(2, account.balances[2021]?.size)   //balances for SMTH and ANY
        assertEquals(2, account.balances[2022]?.size)   //balances for SMTH and ANY

        assertBDEquals("10".toBigDecimal(), account.balances[2021]?.get("SMTH"))
        assertBDEquals("8".toBigDecimal(), account.balances[2021]?.get("ANY"))

        assertBDEquals("3".toBigDecimal(), account.balances[2022]?.get("SMTH"))
        assertBDEquals("8".toBigDecimal(), account.balances[2022]?.get("ANY"))
        assertBDEquals("4".toBigDecimal(), account.gain[2023])
        assertBDEquals("5".toBigDecimal(), account.gainDiscounted[2023])

        //Sell ANY for AUD and incur a loss
        account.registerTradeOperation(
            TradeLog.of(
                "2022-12-12T10:20:00+10:00[Australia/Sydney]",
                "AUD", "1", "ANY", "4", "4", "0.1", "1"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertEquals(2, account.balances.size)  //balances for 2021 and 2022
        assertEquals(2, account.balances[2021]?.size)   //balances for SMTH and ANY
        assertEquals(2, account.balances[2022]?.size)   //balances for SMTH and ANY

        assertBDEquals("10".toBigDecimal(), account.balances[2021]?.get("SMTH"))
        assertBDEquals("8".toBigDecimal(), account.balances[2021]?.get("ANY"))

        assertBDEquals("3".toBigDecimal(), account.balances[2022]?.get("SMTH"))
        assertBDEquals("4".toBigDecimal(), account.gain[2023])
        assertBDEquals("5".toBigDecimal(), account.gainDiscounted[2023])

        assertBDEquals("4".toBigDecimal(), account.balances[2022]?.get("ANY"))
        assertBDEquals("-1.5".toBigDecimal(), account.loss[2023])
    }

    @Test
    fun processReceiveTest() {
        val account = Account()

        account.processReceive(
            ReceiveLog.of(
                "2021-10-10T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "5", "10"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.gain.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertEquals(1, account.balances.size)
        assertEquals(1, account.balances[2021]?.size)
        assertBDEquals("5".toBigDecimal(), account.balances[2021]?.get("SMTH"))

        account.processReceive(
            ReceiveLog.of(
                "2021-10-20T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "7", "21"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.gain.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertEquals(1, account.balances.size)
        assertEquals(1, account.balances[2021]?.size)
        assertBDEquals("12".toBigDecimal(), account.balances[2021]?.get("SMTH"))

        account.processReceive(
            ReceiveLog.of(
                "2022-10-30T10:00:00+10:00[Australia/Sydney]",
                "ANY", "7", "21"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.gain.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertEquals(2, account.balances.size)
        assertEquals(1, account.balances[2021]?.size)
        assertEquals(2, account.balances[2022]?.size)
        assertBDEquals("12".toBigDecimal(), account.balances[2021]?.get("SMTH"))
        assertBDEquals("12".toBigDecimal(), account.balances[2022]?.get("SMTH"))
        assertBDEquals("7".toBigDecimal(), account.balances[2022]?.get("ANY"))
    }

    @Test
    fun processSendFiFoTest() {
        val account = Account()

        assertThrows(TradeOperationLogException::class.java) {
            account.processSendFiFo(
                SendLog.of(
                    "2021-10-10T10:00:00+10:00[Australia/Sydney]",
                    "SMTH", "5", "10"
                )
            )
        }

        //Given we received SMTH and ANY coins
        account.processReceive(
            ReceiveLog.of(
                "2021-10-10T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "5", "10"
            )
        )

        account.processReceive(
            ReceiveLog.of(
                "2021-10-20T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "7", "21"
            )
        )

        account.processReceive(
            ReceiveLog.of(
                "2022-10-10T10:00:00+10:00[Australia/Sydney]",
                "ANY", "7", "21"
            )
        )

        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.gain.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertEquals(2, account.balances.size)
        assertEquals(1, account.balances[2021]?.size)
        assertEquals(2, account.balances[2022]?.size)
        assertBDEquals("12".toBigDecimal(), account.balances[2021]?.get("SMTH"))
        assertBDEquals("12".toBigDecimal(), account.balances[2022]?.get("SMTH"))
        assertBDEquals("7".toBigDecimal(), account.balances[2022]?.get("ANY"))

        account.processSendFiFo(
            SendLog.of(
                "2022-10-20T10:00:00+10:00[Australia/Sydney]",
                "ANY", "5", "21"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.gain.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertEquals(2, account.balances.size)
        assertEquals(1, account.balances[2021]?.size)
        assertEquals(2, account.balances[2022]?.size)
        assertBDEquals("12".toBigDecimal(), account.balances[2021]?.get("SMTH"))
        assertBDEquals("12".toBigDecimal(), account.balances[2022]?.get("SMTH"))
        assertBDEquals("2".toBigDecimal(), account.balances[2022]?.get("ANY"))

        account.processSendFiFo(
            SendLog.of(
                "2022-10-30T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "12", "12"
            )
        )

        assertFalse(account.balances.isEmpty())
        assertTrue(account.errorLog.isEmpty())
        assertTrue(account.gain.isEmpty())
        assertTrue(account.gainDiscounted.isEmpty())
        assertTrue(account.loss.isEmpty())
        assertEquals(2, account.balances.size)
        assertEquals(1, account.balances[2021]?.size)
        assertEquals(2, account.balances[2022]?.size)
        assertBDEquals("12".toBigDecimal(), account.balances[2021]?.get("SMTH"))
        assertBDEquals(ZERO, account.balances[2022]?.get("SMTH"))
        assertBDEquals("2".toBigDecimal(), account.balances[2022]?.get("ANY"))
    }

    @Test
    fun `getYearlyBalances$tax_calculator`() {
        val account = Account()

        val balances2017 = account.getYearlyBalances(2017)
        balances2017["BTC"] = BigDecimal.TEN

        val balances2018 = account.getYearlyBalances(2018)
        assertEquals(BigDecimal.TEN, balances2018["BTC"])

        val balances2021 = account.getYearlyBalances(2021)
        assertEquals(BigDecimal.TEN, balances2021["BTC"])

        balances2018["BTC"] = BigDecimal.ONE

        // After changing balance in 2018, balance in 2019 should not change,
        // because it was copied from 2019 earlier
        val balances2019 = account.getYearlyBalances(2019)
        assertBDEquals(BigDecimal.TEN, balances2019["BTC"])
    }

    private fun assertBDEquals(expected: BigDecimal, actual: BigDecimal?) {
        assertTrue(expected.compareTo(actual) == 0, "Expected $expected, but was $actual")
    }
}