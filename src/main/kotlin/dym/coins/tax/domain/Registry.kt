package dym.coins.tax.domain

import dym.coins.tax.dto.ReceiveLog
import dym.coins.tax.dto.SendLog
import dym.coins.tax.dto.TradeLog

/**
 * @author dym
 * Date: 27.09.2023
 */
interface Registry {
    fun register(op: TradeLog)
    fun register(op: ReceiveLog)
    fun register(op: SendLog)
}