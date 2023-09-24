package dym.coins.exceptions

import dym.coins.tax.dto.OrderedLog

/**
 * @author dym
 * Date: 21.09.2023
 */

class  TradeOperationLogException (message: String?, cause: Throwable?, val op: OrderedLog?)
    : CoinException(message, cause) {

    constructor(message: String?, op: OrderedLog?) : this(message, null, op)
}