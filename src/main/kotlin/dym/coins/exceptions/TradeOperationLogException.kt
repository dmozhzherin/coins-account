package dym.coins.exceptions

import dym.coins.tax.dto.LogOperation

/**
 * @author dym
 * Date: 21.09.2023
 */
class TradeOperationLogException (message: String?, cause: Throwable?, op: LogOperation?)
    : CoinException(message, cause) {

    constructor(message: String?, op: LogOperation?) : this(message, null, op)

}