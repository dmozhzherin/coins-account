package dym.coins.tax.extensions

import dym.coins.tax.Config
import java.math.BigDecimal

/**
 * @author dym
 * Date: 24.09.2023
 */

fun BigDecimal.toCurrency(): BigDecimal = this.setScale(2, Config.DEFAULT_ROUNDING_MODE)

fun BigDecimal.normalize(): BigDecimal = this.setScale(Config.DEFAULT_DECIMAL_SCALE, Config.DEFAULT_ROUNDING_MODE)