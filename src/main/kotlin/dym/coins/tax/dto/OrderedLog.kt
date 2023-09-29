package dym.coins.tax.dto

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * @author dym
 * Date: 24.09.2023
 */
interface OrderedLog: Comparable<OrderedLog>{
    val timestamp: ZonedDateTime
    val date: LocalDate
        get() = timestamp.toLocalDate()

    override fun compareTo(other: OrderedLog) = timestamp.compareTo(other.timestamp)
}