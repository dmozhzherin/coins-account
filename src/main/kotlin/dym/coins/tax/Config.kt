package dym.coins.tax

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * @author dym
 * Date: 24.09.2023
 */
class Config {
    companion object {
        const val DEFAULT_TIMEZONE = "Australia/Sydney"
        const val DEFAULT_DECIMAL_SCALE = 16
        val DEFAULT_ROUNDING_MODE = RoundingMode.HALF_EVEN
        val ERROR_THRESHOLD = BigDecimal("1e-8")
    }
}