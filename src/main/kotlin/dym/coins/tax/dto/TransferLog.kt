package dym.coins.tax.dto

import dym.coins.tax.domain.AssetType
import java.math.BigDecimal

/**
 * @author dym
 * Date: 24.09.2023
 */
interface TransferLog : OrderedLog{
    val asset: AssetType
    val amount: BigDecimal
    val capital: BigDecimal
}