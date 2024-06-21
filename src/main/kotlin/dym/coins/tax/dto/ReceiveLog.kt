package dym.coins.tax.dto

import dym.coins.coinspot.domain.AssetType
import dym.coins.tax.domain.Registry
import dym.coins.tax.extensions.normalize
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * @author dym
 * Date: 24.09.2023
 */
@JvmRecord
data class ReceiveLog(
    override val timestamp: ZonedDateTime,
    override val incomingAsset: AssetType,
    override val incomingAmount: BigDecimal,
    override val capital: BigDecimal
) : TransferLog, IncomingLog, Registerable {

    init {
        require(capital >= BigDecimal.ZERO) { "Capital must be non-negative" }
        require(incomingAmount > BigDecimal.ZERO) { "Incoming amount must be positive" }
    }

    override val asset: AssetType
        get() = incomingAsset

    override val amount: BigDecimal
        get() = incomingAmount

    override fun registerIn(registry: Registry) = registry.register(this)

    companion object {
        fun of(timestamp: String, coin: String, amount: String, capital: String) = ReceiveLog(
            ZonedDateTime.parse(timestamp),
            AssetType.of(coin),
            amount.toBigDecimal().normalize(),
            capital.toBigDecimal().normalize()
        )
    }

}
