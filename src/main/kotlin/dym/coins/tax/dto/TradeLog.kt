package dym.coins.tax.dto

import dym.coins.tax.extensions.normalize
import java.math.BigDecimal
import java.math.MathContext
import java.time.ZonedDateTime

/**
 * @author dym
 * Date: 26.10.2022
 */

@JvmRecord
data class TradeLog(
    override val timestamp: ZonedDateTime,
    /**
     * Coin bought. E.g. if we bought BTC for AUD, then it is BTC. If we sold BTC for AUD, then it is AUD
     */
    val buy: String,
    val buyAmount: BigDecimal,

    /**
     * Coin sold. E.g. if we bought BTC for AUD, then it is AUD. If we sold BTC for AUD, then it is BTC
     */
    val sell: String,
    val sellAmount: BigDecimal,
    val rate: BigDecimal,
    val fee: BigDecimal,
    /**
     * Capital in AUD. The equivalent of the buy/sell amount in AUD
     */
    override val capital: BigDecimal
) : OrderedLog, IncomingLog {
    init {
        if (buy == sell) {
            throw IllegalArgumentException("Buy and sell coins are the same $buy")
        }
    }

    override fun incomingCoin() = buy

    override fun incomingAmount() = buyAmount

    companion object {
        fun of(
            timestamp: String,
            buy: String,
            buyAmount: String,
            sell: String,
            sellAmount: String,
            rate: String,
            fee: String,
            capital: String
        ) = TradeLog(
            ZonedDateTime.parse(timestamp),
            buy,
            buyAmount.toBigDecimal(MathContext.DECIMAL128).normalize(),
            sell,
            sellAmount.toBigDecimal(MathContext.DECIMAL128).normalize(),
            rate.toBigDecimal(MathContext.DECIMAL128).normalize(),
            fee.toBigDecimal(MathContext.DECIMAL128).normalize(),
            capital.toBigDecimal(MathContext.DECIMAL128).normalize()
        )
    }
}
