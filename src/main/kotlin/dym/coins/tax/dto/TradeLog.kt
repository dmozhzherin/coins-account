package dym.coins.tax.dto

import dym.coins.tax.domain.AssetType
import dym.coins.tax.domain.Registry
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
    override val incomingAsset: AssetType,
    override val incomingAmount: BigDecimal,

    /**
     * Coin sold. E.g. if we bought BTC for AUD, then it is AUD. If we sold BTC for AUD, then it is BTC
     */
    override val outgoingAsset: AssetType,
    override val outgoingAmount: BigDecimal,

    val rate: BigDecimal,
    val fee: BigDecimal,

    /**
     * Capital in AUD. The equivalent of the buy/sell amount in AUD
     */
    override val capital: BigDecimal

) : IncomingLog, OutgoingLog, Registerable {



    init {
        require(incomingAmount > BigDecimal.ZERO) { "Incoming amount must be positive" }
        require(outgoingAmount > BigDecimal.ZERO) { "Outgoing amount must be positive" }
        require(rate > BigDecimal.ZERO) { "Rate must be positive" }
        require(fee >= BigDecimal.ZERO) { "Fee must be non-negative" }
        require(capital >= BigDecimal.ZERO) { "Capital must be non-negative" }
        require(incomingAsset != outgoingAsset) { "Incoming and outgoing assets must be different" }
    }

    override fun registerIn(registry: Registry) = registry.register(this)

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
            AssetType.of(buy),
            buyAmount.toBigDecimal(MathContext.DECIMAL128).normalize(),
            AssetType.of(sell),
            sellAmount.toBigDecimal(MathContext.DECIMAL128).normalize(),
            rate.toBigDecimal(MathContext.DECIMAL128).normalize(),
            fee.toBigDecimal(MathContext.DECIMAL128).normalize(),
            capital.toBigDecimal(MathContext.DECIMAL128).normalize()
        )
    }
}
