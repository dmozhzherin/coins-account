package dym.coins.tax.dto

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
    override val coin: String,
    override val amount: BigDecimal,
    override val capital: BigDecimal?
) : TransferLog {

    companion object {
        fun of(timestamp: String, coin: String, amount: String, capital: String) = SendLog(
            ZonedDateTime.parse(timestamp),
            coin,
            amount.toBigDecimal().normalize(),
            capital.toBigDecimal().normalize()
        )
    }
}
