package dym.coins.connectors

import dym.coins.coinspot.api.resource.OrderHistoryResponse.TradeOperation
import dym.coins.coinspot.api.resource.TransfersHistoryResponse
import dym.coins.tax.Config.Companion.DEFAULT_TIMEZONE
import dym.coins.tax.dto.ReceiveLog
import dym.coins.tax.dto.SendLog
import dym.coins.tax.dto.TradeLog
import dym.coins.tax.extensions.normalize
import dym.coins.tax.extensions.toCurrency
import java.math.MathContext
import java.time.ZoneId

/**
 * @author dym
 * Date: 20.09.2023
 */
class CoinspotConverter (private val timeZone: ZoneId? = ZoneId.of(DEFAULT_TIMEZONE)) {

    fun buyLogFrom(tradeOp: TradeOperation): TradeLog {
        return TradeLog(
            timestamp = tradeOp.solddate.withZoneSameInstant(timeZone),
            buy = tradeOp.coin,
            buyAmount = tradeOp.amount.normalize(),
            sell = tradeOp.market.substringAfter('/'),
            sellAmount = tradeOp.total.normalize(),
            rate = tradeOp.rate.normalize(),
            fee = tradeOp.audfeeExGst.add(tradeOp.audGst),
            capital = tradeOp.audtotal.toCurrency()
        )
    }

    fun sellLogFrom(tradeOp: TradeOperation): TradeLog {
        return TradeLog(
            timestamp = tradeOp.solddate.withZoneSameInstant(timeZone),
            buy = tradeOp.market.substringAfter('/'),
            buyAmount = tradeOp.total.normalize(),
            sell = tradeOp.coin,
            sellAmount = tradeOp.amount.normalize(),
            rate = tradeOp.rate.pow(-1, MathContext.DECIMAL128).normalize(),
            fee = tradeOp.audfeeExGst.add(tradeOp.audGst).normalize(),
            capital = tradeOp.audtotal.toCurrency()
        )
    }

    fun sendLogFrom(transferOp: TransfersHistoryResponse.TransferOperation): SendLog {
        return SendLog(
            timestamp = transferOp.timestamp.withZoneSameInstant(timeZone),
            coin = transferOp.coin,
            amount = transferOp.amount.normalize(),
            capital = transferOp.aud?.toCurrency(),
        )
    }
    fun receiveLogFrom(transferOp: TransfersHistoryResponse.TransferOperation): ReceiveLog {
        return ReceiveLog(
            timestamp = transferOp.timestamp.withZoneSameInstant(timeZone),
            coin = transferOp.coin,
            amount = transferOp.amount.normalize(),
            capital = transferOp.aud?.toCurrency()!!,
        )
    }
}