package dym.coins.tax.dto

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * @author dym
 * Date: 24.09.2023
 */
interface OrderedLog {
    val timestamp: ZonedDateTime
    fun date(): LocalDate = timestamp.toLocalDate()
}