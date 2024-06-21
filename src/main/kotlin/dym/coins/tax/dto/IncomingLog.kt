package dym.coins.tax.dto

import dym.coins.coinspot.domain.AssetType
import java.math.BigDecimal

/**
 * @author dym
 * Date: 24.09.2023
 */
interface IncomingLog: OrderedLog {
    val capital: BigDecimal
    val incomingAsset: AssetType
    val incomingAmount: BigDecimal
}