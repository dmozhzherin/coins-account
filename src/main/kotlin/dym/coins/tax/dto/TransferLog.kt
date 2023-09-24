package dym.coins.tax.dto

import java.math.BigDecimal

/**
 * @author dym
 * Date: 24.09.2023
 */
interface TransferLog : OrderedLog{
    val coin: String
    val amount: BigDecimal
    val capital: BigDecimal?
}