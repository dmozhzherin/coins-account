package dym.coins.tax.dto

import dym.coins.tax.domain.AssetType
import dym.coins.tax.domain.Registry
import dym.coins.tax.extensions.normalize
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * @author dym
 * Date: 24.09.2023
 */
@JvmRecord
data class SendLog(
    override val timestamp: ZonedDateTime,
    override val outgoingAsset: AssetType,
    override val outgoingAmount: BigDecimal,
    override val capital: BigDecimal

) : TransferLog, OutgoingLog, Registerable {

    init{
        require(capital >= BigDecimal.ZERO) { "Capital must be non-negative" }
        require(outgoingAmount > BigDecimal.ZERO) { "Outgoing amount must be positive" }
    }

    override val asset: AssetType
        get() = outgoingAsset
    override val amount: BigDecimal
        get() = outgoingAmount

    override fun registerIn(registry: Registry) = registry.register(this)

    companion object {
        fun of(timestamp: String, coin: String, amount: String, capital: String) = SendLog(
            ZonedDateTime.parse(timestamp),
            AssetType.of(coin),
            amount.toBigDecimal().normalize(),
            capital.toBigDecimal().normalize()
        )
    }

}
