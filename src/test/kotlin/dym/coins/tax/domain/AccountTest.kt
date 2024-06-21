package dym.coins.tax.domain

import dym.coins.coinspot.domain.AssetType
import dym.coins.exceptions.TradeOperationLogException
import dym.coins.tax.dto.ReceiveLog
import dym.coins.tax.dto.SendLog
import dym.coins.tax.dto.TradeLog
import org.junit.jupiter.api.Assertions.assertThrows
import java.math.BigDecimal
import java.math.BigDecimal.ZERO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * @author dym
 * Date: 23.09.2023
 */
class AccountTest {

    @Test
    fun registerTradeOperation_ensureOrderConsistency() {
        val account = Account()

        account.register(
            TradeLog.of(
                "2021-10-10T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "1", "AUD", "1", "1", "1", "1"
            )
        )

        //Given operation date is after the last operation when registering the operation expect no exception
        account.register(
            TradeLog.of(
                "2021-10-20T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "1", "AUD", "1", "1", "1", "1"
            )
        )

        //Given operation date is before the last operation when registering the operation expect TradeOperationLogException
        assertThrows(TradeOperationLogException::class.java) {
            account.register(
                TradeLog.of(
                    "2021-10-05T10:00:00+10:00[Australia/Sydney]",
                    "SMTH", "1", "AUD", "1", "1", "1", "1"
                )
            )
        }
    }

    @Test
    fun registerTradeOperation_ensureNegativeBalanceVerification() {
        val account = Account()

        account.register(
            TradeLog.of(
                "2021-10-10T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "5", "AUD", "10", "2", "0.1", "10"
            )
        )

        assertFalse(account.years().isEmpty())
        assertTrue(account.gain(2022).isEmpty())
        assertTrue(account.loss(2022).isEmpty())
        assertTrue(account.gainDiscounted(2022).isEmpty())
        assertEquals(1, account.balances(2022).size)
        assertBDEquals(BigDecimal(5), account.balances(2022)[SMTH])

        //Given a log operation with correct date, SMTH coin as sell, ANY coin as buy,
        //sellAmount greater than the current balance, any rate, any fee, any capital
        //When registering the operation
        //Then expect assertion error
        assertThrows(AssertionError::class.java) {
            account.register(
                TradeLog.of(
                    "2021-10-10T10:10:00+10:00[Australia/Sydney]",
                    "ANY", "5", "SMTH", "10", "2", "0.1", "10"
                )
            )
        }

        //Assertion error is fatal, but if -ea is not set, then the error is logged and the balances are updated
        //The consistency of the account is not guaranteed in this case.
        assertBDEquals(BigDecimal(-5), account.balances(2022)[SMTH])

        assertFalse(account.balances(2022).isEmpty())
        assertTrue(account.gain(2022).isEmpty())
        assertTrue(account.loss(2022).isEmpty())
        assertTrue(account.gainDiscounted(2022).isEmpty())
        assertEquals(1, account.balances(2022).size)
    }

    @Test
    fun registerTradeOperation_buyAndSellWithinAYear() {
        val account = Account()

        account.register(
            TradeLog.of(
                "2021-01-10T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "5", "AUD", "10", "2", "0.1", "10"
            )
        )

        account.register(
            TradeLog.of(
                "2021-01-10T10:10:00+10:00[Australia/Sydney]",
                "SMTH", "5", "AUD", "5", "1", "0.1", "5"
            )
        )

        assertFalse(account.balances(2021).isEmpty())
        assertTrue(account.gain(2021).isEmpty())
        assertTrue(account.loss(2021).isEmpty())
        assertTrue(account.gainDiscounted(2021).isEmpty())
        assertEquals(1, account.years().size)
        assertEquals(1, account.balances(2021).size)
        assertBDEquals("10".toBigDecimal(), account.balances(2021)[SMTH])

        account.register(
            TradeLog.of(
                "2021-01-10T10:20:00+10:00[Australia/Sydney]",
                "ANY", "8", "AUD", "8", "1", "0.1", "5"
            )
        )

        assertFalse(account.balances(2021).isEmpty())
        assertTrue(account.gain(2021).isEmpty())
        assertTrue(account.loss(2021).isEmpty())
        assertTrue(account.gainDiscounted(2021).isEmpty())
        assertEquals(1, account.years().size)
        assertEquals(2, account.balances(2021).size)
        assertBDEquals("10".toBigDecimal(), account.balances(2021)[SMTH])
        assertBDEquals("8".toBigDecimal(), account.balances(2021)[ANY])

        //Given a log operation with correct date, SMTH coin as sell, AUD as buy,
        //sellAmount less than the current SMTH balance, any rate, any fee, any capital
        //When registering the operation
        //Then the operation is registered, balances are updated, no errors are logged and gain is recorded
        account.register(
            TradeLog.of(
                "2021-01-10T10:30:00+10:00[Australia/Sydney]",
                "AUD", "15", "SMTH", "5", "3", "0.1", "15"
            )
        )

        assertFalse(account.years().isEmpty())
        assertTrue(account.loss(2021).isEmpty())
        assertTrue(account.gainDiscounted(2021).isEmpty())
        assertEquals(1, account.years().size)
        assertEquals(2, account.balances(2021).size)
        assertBDEquals(BigDecimal(5), account.balances(2021)[SMTH])
        assertBDEquals(BigDecimal(8), account.balances(2021)[ANY])
        assertBDEquals(BigDecimal(5), account.gainTotal(2021))
        assertBDEquals(BigDecimal(5), account.gain(2021)[SMTH])
        assertNull(account.gain(2021)[ANY])
    }

    @Test
    fun registerTradeOperation_buyAndSellAfterAYear() {
        val account = Account()

        account.register(
            TradeLog.of(
                "2021-01-10T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "5", "AUD", "10", "2", "0.1", "10"
            )
        )

        account.register(
            TradeLog.of(
                "2021-02-11T10:10:00+10:00[Australia/Sydney]",
                "SMTH", "5", "AUD", "5", "1", "0.1", "5"
            )
        )

        assertFalse(account.years().isEmpty())
        assertTrue(account.gain(2021).isEmpty())
        assertTrue(account.loss(2021).isEmpty())
        assertTrue(account.gainDiscounted(2021).isEmpty())
        assertEquals(1, account.years().size)
        assertEquals(1, account.balances(2021).size)
        assertBDEquals("10".toBigDecimal(), account.balances(2021)[SMTH])

        account.register(
            TradeLog.of(
                "2021-03-12T10:20:00+10:00[Australia/Sydney]",
                "ANY", "8", "AUD", "5", "0.625", "0.1", "5"
            )
        )

        assertFalse(account.years().isEmpty())
        assertTrue(account.gain(2021).isEmpty())
        assertTrue(account.loss(2021).isEmpty())
        assertTrue(account.gainDiscounted(2021).isEmpty())
        assertEquals(1, account.years().size)
        assertEquals(2, account.balances(2021).size)
        assertBDEquals("10".toBigDecimal(), account.balances(2021)[SMTH])
        assertBDEquals("8".toBigDecimal(), account.balances(2021)[ANY])

        //Given a log operation with correct date, SMTH coin as sell, AUD as buy,
        //sellAmount less than the current SMTH balance, any rate, any fee, any capital
        //When registering the operation
        //Then the operation is registered, balances are updated, no errors are logged and gain is recorded
        account.register(
            TradeLog.of(
                "2022-01-11T10:30:00+10:00[Australia/Sydney]",
                "AUD", "21", "SMTH", "7", "3", "0.1", "21"
            )
        )

        assertFalse(account.years().isEmpty())
        assertTrue(account.loss(2021).isEmpty())
        assertEquals(2, account.years().size)  //balances for 2021 and 2022
        assertEquals(2, account.balances(2021).size)   //balances for SMTH and ANY
        assertEquals(2, account.balances(2022).size)   //balances for SMTH and ANY

        assertBDEquals(BigDecimal(10), account.balances(2021)[SMTH])
        assertBDEquals(BigDecimal(8), account.balances(2021)[ANY])

        assertBDEquals(BigDecimal(3), account.balances(2022)[SMTH])
        assertBDEquals(BigDecimal(8), account.balances(2022)[ANY])
        assertBDEquals(BigDecimal(4), account.gainTotal(2022))
        assertBDEquals(BigDecimal(5), account.gainDiscountedTotal(2022))

        //Sell ANY for AUD and incur a loss
        account.register(
            TradeLog.of(
                "2022-03-12T10:20:00+10:00[Australia/Sydney]",
                "AUD", "1", "ANY", "4", "4", "0.1", "1"
            )
        )

        assertFalse(account.years().isEmpty())
        assertEquals(2, account.years().size)  //balances for 2021 and 2022
        assertEquals(2, account.balances(2021).size)   //balances for SMTH and ANY
        assertEquals(2, account.balances(2022).size)   //balances for SMTH and ANY

        assertBDEquals("10".toBigDecimal(), account.balances(2021)[SMTH])
        assertBDEquals("8".toBigDecimal(), account.balances(2021)[ANY])

        assertBDEquals("3".toBigDecimal(), account.balances(2022)[SMTH])
        assertBDEquals("4".toBigDecimal(), account.gainTotal(2022))
        assertBDEquals("5".toBigDecimal(), account.gainDiscountedTotal(2022))

        assertBDEquals("4".toBigDecimal(), account.balances(2022)[ANY])
        assertBDEquals("-1.5".toBigDecimal(), account.lossTotal(2022))
    }

    @Test
    fun processReceiveTest() {
        val account = Account()

        account.register(
            ReceiveLog.of(
                "2021-01-10T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "5", "10"
            )
        )

        assertFalse(account.years().isEmpty())
        assertTrue(account.gain(2021).isEmpty())
        assertTrue(account.loss(2021).isEmpty())
        assertTrue(account.gainDiscounted(2021).isEmpty())
        assertEquals(1, account.years().size)
        assertEquals(1, account.balances(2021).size)
        assertBDEquals(BigDecimal(5), account.balances(2021)[SMTH])

        account.register(
            ReceiveLog.of(
                "2021-01-20T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "7", "21"
            )
        )

        assertFalse(account.years().isEmpty())
        assertTrue(account.gain(2021).isEmpty())
        assertTrue(account.loss(2021).isEmpty())
        assertTrue(account.gainDiscounted(2021).isEmpty())
        assertEquals(1, account.balances(2021).size)
        assertEquals(1, account.balances(2021).size)
        assertBDEquals("12".toBigDecimal(), account.balances(2021)[SMTH])

        account.register(
            ReceiveLog.of(
                "2022-01-30T10:00:00+10:00[Australia/Sydney]",
                "ANY", "7", "21"
            )
        )

        assertFalse(account.years().isEmpty())
        assertTrue(account.gain(2021).isEmpty())
        assertTrue(account.loss(2021).isEmpty())
        assertTrue(account.gainDiscounted(2021).isEmpty())
        assertEquals(2, account.years().size)
        assertEquals(1, account.balances(2021).size)
        assertEquals(2, account.balances(2022).size)
        assertBDEquals(BigDecimal(12), account.balances(2021)[SMTH])
        assertBDEquals(BigDecimal(12), account.balances(2022)[SMTH])
        assertBDEquals(BigDecimal(7), account.balances(2022)[ANY])
    }

    @Test
    fun processSendFiFoTest() {
        val account = Account()

        assertThrows(TradeOperationLogException::class.java) {
            account.register(
                SendLog.of(
                    "2021-01-10T10:00:00+10:00[Australia/Sydney]",
                    "SMTH", "5", "10"
                )
            )
        }

        //Given we received SMTH and ANY coins
        account.register(
            ReceiveLog.of(
                "2021-01-10T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "5", "10"
            )
        )

        account.register(
            ReceiveLog.of(
                "2021-01-20T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "7", "21"
            )
        )

        account.register(
            ReceiveLog.of(
                "2022-01-10T10:00:00+10:00[Australia/Sydney]",
                "ANY", "7", "21"
            )
        )

        assertTrue(account.gain(2021).isEmpty())
        assertTrue(account.gain(2022).isEmpty())
        assertTrue(account.gainDiscounted(2021).isEmpty())
        assertTrue(account.gainDiscounted(2022).isEmpty())
        assertTrue(account.loss(2021).isEmpty())
        assertTrue(account.loss(2022).isEmpty())
        assertEquals(2, account.years().size)
        assertEquals(1, account.balances(2021).size)
        assertEquals(2, account.balances(2022).size)
        assertBDEquals("12".toBigDecimal(), account.balances(2021)[SMTH])
        assertBDEquals("12".toBigDecimal(), account.balances(2022)[SMTH])
        assertBDEquals("7".toBigDecimal(), account.balances(2022)[ANY])

        account.register(
            SendLog.of(
                "2022-01-20T10:00:00+10:00[Australia/Sydney]",
                "ANY", "5", "21"
            )
        )

        assertFalse(account.years().isEmpty())
        assertTrue(account.gain(2021).isEmpty())
        assertTrue(account.gain(2022).isEmpty())
        assertTrue(account.gainDiscounted(2021).isEmpty())
        assertTrue(account.gainDiscounted(2022).isEmpty())
        assertTrue(account.loss(2021).isEmpty())
        assertTrue(account.loss(2022).isEmpty())
        assertEquals(2, account.years().size)
        assertEquals(1, account.balances(2021).size)
        assertEquals(2, account.balances(2022).size)
        assertBDEquals(BigDecimal(12), account.balances(2021)[SMTH])
        assertBDEquals(BigDecimal(12), account.balances(2022)[SMTH])
        assertBDEquals(BigDecimal(2), account.balances(2022)[ANY])

        account.register(
            SendLog.of(
                "2022-01-30T10:00:00+10:00[Australia/Sydney]",
                "SMTH", "12", "12"
            )
        )

        assertFalse(account.years().isEmpty())
        assertTrue(account.gain(2021).isEmpty())
        assertTrue(account.gain(2022).isEmpty())
        assertTrue(account.gainDiscounted(2021).isEmpty())
        assertTrue(account.gainDiscounted(2022).isEmpty())
        assertTrue(account.loss(2021).isEmpty())
        assertTrue(account.loss(2022).isEmpty())
        assertEquals(2, account.years().size)
        assertEquals(1, account.balances(2021).size)
        assertEquals(2, account.balances(2022).size)
        assertBDEquals(BigDecimal(12), account.balances(2021)[SMTH])
        assertBDEquals(ZERO, account.balances(2022)[SMTH])
        assertBDEquals(BigDecimal(2), account.balances(2022)[ANY])
    }

    private fun assertBDEquals(expected: BigDecimal, actual: BigDecimal?) {
        assertTrue(expected.compareTo(actual) == 0, "Expected $expected, but was $actual")
    }

    companion object {
        val SMTH = AssetType.of("SMTH")
        val ANY = AssetType.of("ANY")
    }
}