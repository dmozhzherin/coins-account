package dym.coins.tax.dto

import java.math.BigDecimal

/**
 * @author dym
 * Date: 24.09.2023
 */
interface IncomingLog: OrderedLog {
    val capital: BigDecimal
    fun incomingCoin(): String
    fun incomingAmount(): BigDecimal



}