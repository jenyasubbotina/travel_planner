package org.travelplanner.app.core

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

fun String.toMoneyDouble(): Double = toDoubleOrNull() ?: 0.0

fun Double.toMoneyString(): String {
    val long = toLong()
    val frac = ((this - long) * 100 + 0.5).toLong()
    return if (frac < 10) "$long.0$frac" else "$long.$frac"
}

fun Long.toIsoInstant(): String = Instant.fromEpochMilliseconds(this).toString()

fun Long.toIsoDate(): String =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

fun String.toEpochMillis(): Long = try {
    Instant.parse(this).toEpochMilliseconds()
} catch (_: Exception) {
    try {
        val date = LocalDate.parse(this)
        date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    } catch (_: Exception) {
        0L
    }
}

fun String?.toLocalDateOrNull(): LocalDate? = this?.let {
    try {
        LocalDate.parse(it)
    } catch (_: Exception) {
        try {
            Instant.parse(it).toLocalDateTime(TimeZone.currentSystemDefault()).date
        } catch (_: Exception) {
            null
        }
    }
}
