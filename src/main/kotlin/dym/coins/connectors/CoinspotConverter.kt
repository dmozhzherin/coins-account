package dym.coins.connectors

import dym.coins.coinspot.api.resource.OrderHistoryResponse.TradeOperation
import dym.coins.tax.dto.LogOperation
import java.math.MathContext
import java.time.ZoneId

/**
 * @author dym
 * Date: 20.09.2023
 */
class CoinspotConverter (private val timeZone: ZoneId) {

    fun buyOrderFrom(tradeOp: TradeOperation): LogOperation {
        return LogOperation(
            timestamp = tradeOp.solddate.withZoneSameInstant(timeZone),
            buy = tradeOp.coin,
            sell = tradeOp.market.substringAfter('/'),
            buyAmount = tradeOp.amount,
            sellAmount = tradeOp.total,
            rate = tradeOp.rate,
            fee = tradeOp.audfeeExGst.add(tradeOp.audGst),
            capital = tradeOp.audtotal
        )
    }

    fun sellOrderFrom(tradeOp: TradeOperation): LogOperation {
        return LogOperation(
            timestamp = tradeOp.solddate.withZoneSameInstant(timeZone),
            buy = tradeOp.market.substringAfter('/'),
            sell = tradeOp.coin,
            buyAmount = tradeOp.total,
            sellAmount = tradeOp.amount,
            rate = tradeOp.rate.pow(-1, MathContext.DECIMAL128),
            fee = tradeOp.audfeeExGst.add(tradeOp.audGst),
            capital = tradeOp.audtotal
        )
    }
}