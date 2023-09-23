package dym.coins.exceptions

/**
 * @author dym
 * Date: 21.09.2023
 */
open class CoinException(message: String?, cause: Throwable?) : Exception(message, cause) {
    constructor(message: String?) : this(message, null)

}