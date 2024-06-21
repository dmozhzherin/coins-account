package dym.coins.tax.dto

import dym.coins.coinspot.domain.AssetType
import java.math.BigDecimal

/**
 * @author dym
 * Date: 26.09.2023
 */
interface OutgoingLog: OrderedLog {
    val capital: BigDecimal
    val outgoingAsset: AssetType
    val outgoingAmount: BigDecimal
}