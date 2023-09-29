package dym.coins.tax.dto

import dym.coins.tax.domain.Registry

/**
 * @author dym
 * Date: 27.09.2023
 */
interface Registerable {
    fun registerIn(registry: Registry)
}