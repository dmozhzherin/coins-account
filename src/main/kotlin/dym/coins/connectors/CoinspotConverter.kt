package dym.coins.connectors

import dym.coins.coinspot.api.dto.TradeOperation
import dym.coins.coinspot.api.dto.TransferOperation
import dym.coins.coinspot.domain.AssetType
import dym.coins.tax.Config
import dym.coins.tax.Config.Companion.DEFAULT_TIMEZONE
import dym.coins.tax.dto.ReceiveLog
import dym.coins.tax.dto.SendLog
import dym.coins.tax.dto.TradeLog
import dym.coins.tax.extensions.normalize
import dym.coins.tax.extensions.toCurrency
import java.math.BigDecimal
import java.time.ZoneId

/**
 * @author dym
 * Date: 20.09.2023
 */
class CoinspotConverter (private val timeZone: ZoneId? = ZoneId.of(DEFAULT_TIMEZONE)) {

    fun buyLogFrom(tradeOp: TradeOperation): TradeLog {
        return TradeLog(
            timestamp = tradeOp.solddate.atZone(timeZone),
            incomingAsset = tradeOp.coin,
            incomingAmount = tradeOp.amount.normalize(),
            outgoingAsset = AssetType.of(tradeOp.market.substringAfter('/')),
            outgoingAmount = tradeOp.total.normalize(),
            rate = tradeOp.rate.normalize(),
            fee = tradeOp.audfeeExGst.add(tradeOp.audGst),
            capital = tradeOp.audtotal.toCurrency()
        )
    }

    fun sellLogFrom(tradeOp: TradeOperation): TradeLog {
        return TradeLog(
            timestamp = tradeOp.solddate.atZone(timeZone),
            incomingAsset = AssetType.of(tradeOp.market.substringAfter('/')),
            incomingAmount = tradeOp.total.normalize(),
            outgoingAsset = tradeOp.coin,
            outgoingAmount = tradeOp.amount.normalize(),
            rate = BigDecimal.ONE.normalize().divide(tradeOp.rate, Config.DEFAULT_ROUNDING_MODE),
            fee = tradeOp.audfeeExGst.add(tradeOp.audGst).normalize(),
            capital = tradeOp.audtotal.toCurrency()
        )
    }

    fun sendLogFrom(transferOp: TransferOperation): SendLog {
        return SendLog(
            timestamp = transferOp.timestamp.atZone(timeZone),
            outgoingAsset = transferOp.coin,
            outgoingAmount = transferOp.amount.normalize(),
            capital = transferOp.aud.toCurrency(),
        )
    }
    fun receiveLogFrom(transferOp: TransferOperation): ReceiveLog {
        return ReceiveLog(
            timestamp = transferOp.timestamp.atZone(timeZone),
            incomingAsset = transferOp.coin,
            incomingAmount = transferOp.amount.normalize(),
            capital = transferOp.aud.toCurrency(),
        )
    }
}