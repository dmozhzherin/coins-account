package dym.coins.tax

import dym.coins.tax.domain.Account
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * @author dym
 * Date: 22.09.2023
 */
class TaxCalculatorTest {

    @Test
    fun registerTradeOperation() {
    }

    @Test
    fun `getYearlyBalances$tax_calculator`() {
        val taxCalculator = Account()

        val balances2017 = taxCalculator.getYearlyBalances(2017)
        balances2017["BTC"] = BigDecimal.TEN

        val balances2018 = taxCalculator.getYearlyBalances(2018)
        assertEquals(BigDecimal.TEN, balances2018["BTC"])

        val balances2021 = taxCalculator.getYearlyBalances(2021)
        assertEquals(BigDecimal.TEN, balances2021["BTC"])

        balances2018["BTC"] = BigDecimal.ONE

        // After changing balance in 2018, balance in 2019 should not change,
        // because it was copied from 2019 earlier
        val balances2019 = taxCalculator.getYearlyBalances(2019)
        assertEquals(BigDecimal.TEN, balances2019["BTC"])
    }
}