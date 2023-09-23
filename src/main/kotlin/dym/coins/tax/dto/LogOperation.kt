package dym.coins.tax.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * @author dym
 * Date: 26.10.2022
 */

@JvmRecord
data class LogOperation(
    val timestamp: ZonedDateTime,
    /**
     * Coin bought. E.g. if we bought BTC for AUD, then it is BTC. If we sold BTC for AUD, then it is AUD
     */
    val buy: String,
    /**
     * Coin sold. E.g. if we bought BTC for AUD, then it is AUD. If we sold BTC for AUD, then it is BTC
     */
    val sell: String,

    val buyAmount: BigDecimal,
    val sellAmount: BigDecimal,
    val rate: BigDecimal,
    val fee: BigDecimal,
    /**
     * Capital in AUD. The equivalent of the buy/sell amount in AUD
     */
    val capital: BigDecimal
) {
    val date: LocalDate get() = timestamp.toLocalDate()
}
