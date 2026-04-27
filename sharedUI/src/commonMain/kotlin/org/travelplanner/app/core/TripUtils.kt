package org.travelplanner.app.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

object TripUtils {
    private fun monthAbbrRu(monthNumber: Int): String =
        when (monthNumber) {
            1 -> "янв"
            2 -> "фев"
            3 -> "мар"
            4 -> "апр"
            5 -> "май"
            6 -> "июн"
            7 -> "июл"
            8 -> "авг"
            9 -> "сен"
            10 -> "окт"
            11 -> "ноя"
            12 -> "дек"
            else -> "мес"
        }

    private fun monthGenitiveRu(monthNumber: Int): String =
        when (monthNumber) {
            1 -> "января"
            2 -> "февраля"
            3 -> "марта"
            4 -> "апреля"
            5 -> "мая"
            6 -> "июня"
            7 -> "июля"
            8 -> "августа"
            9 -> "сентября"
            10 -> "октября"
            11 -> "ноября"
            12 -> "декабря"
            else -> ""
        }

    fun formatDateTime(millis: Long): String {
        val instant = Instant.fromEpochMilliseconds(millis)
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.dayOfMonth} ${monthAbbrRu(dt.monthNumber)}, ${dt.hour}:${
            dt.minute.toString().padStart(2, '0')
        }"
    }

    fun Long.toReadableDateRu(): String {
        val instant = Instant.fromEpochMilliseconds(this)
        val date = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${date.dayOfMonth} ${monthAbbrRu(date.month.number)}"
    }

    fun formatDateRangeRu(
        startIso: String?,
        endIso: String?,
    ): String {
        if (startIso.isNullOrBlank() && endIso.isNullOrBlank()) return ""
        val start = parseLocalDate(startIso)
        val end = parseLocalDate(endIso)
        return when {
            start != null && end != null -> {
                if (start.month == end.month && start.year == end.year) {
                    "${start.dayOfMonth} — ${end.dayOfMonth} ${monthGenitiveRu(start.month.number)}"
                } else {
                    "${start.dayOfMonth} ${monthAbbrRu(start.month.number)} — ${end.dayOfMonth} ${
                        monthAbbrRu(
                            end.month.number,
                        )
                    }"
                }
            }

            start != null -> {
                "${start.dayOfMonth} ${monthGenitiveRu(start.month.number)}"
            }

            end != null -> {
                "${end.dayOfMonth} ${monthGenitiveRu(end.month.number)}"
            }

            else -> {
                ""
            }
        }
    }

    fun tripDayCount(
        startIso: String?,
        endIso: String?,
    ): Int {
        val start = parseLocalDate(startIso) ?: return 1
        val end = parseLocalDate(endIso) ?: return 1
        val days = start.daysUntil(end) + 1
        return if (days < 1) 1 else days
    }

    fun pluralizeDays(n: Int): String {
        val abs = if (n < 0) -n else n
        val mod100 = abs % 100
        val mod10 = abs % 10
        return when {
            mod100 in 11..19 -> "дней"
            mod10 == 1 -> "день"
            mod10 in 2..4 -> "дня"
            else -> "дней"
        }
    }

    fun formatNumber(amount: Double): String {
        val rounded = amount.toLong()
        val negative = rounded < 0
        val absStr = (if (negative) -rounded else rounded).toString()
        val grouped = StringBuilder()
        val len = absStr.length
        for (i in 0 until len) {
            if (i > 0 && (len - i) % 3 == 0) grouped.append(' ')
            grouped.append(absStr[i])
        }
        return if (negative) "-$grouped" else grouped.toString()
    }

    fun formatMoney(
        amount: Double,
        currency: String,
    ): String {
        val n = formatNumber(amount)
        return if (n.startsWith("-")) "-$currency${n.drop(1)}" else "$currency$n"
    }

    fun formatDateRangeRuAbbr(
        startIso: String?,
        endIso: String?,
    ): String {
        if (startIso.isNullOrBlank() && endIso.isNullOrBlank()) return ""
        val start = parseLocalDate(startIso)
        val end = parseLocalDate(endIso)
        return when {
            start != null && end != null -> {
                "${start.dayOfMonth} ${monthAbbrRu(start.month.number)}. — " +
                    "${end.dayOfMonth} ${monthAbbrRu(end.month.number)}."
            }

            start != null -> {
                "${start.dayOfMonth} ${monthAbbrRu(start.month.number)}."
            }

            end != null -> {
                "${end.dayOfMonth} ${monthAbbrRu(end.month.number)}."
            }

            else -> {
                ""
            }
        }
    }

    private fun parseLocalDate(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return try {
            LocalDate.parse(iso.substringBefore("T"))
        } catch (_: Exception) {
            null
        }
    }

    fun formatDate(epochMillis: Long): String {
        val instant = Instant.fromEpochMilliseconds(epochMillis)
        val date = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        return "${date.dayOfMonth.toString().padStart(2, '0')}.${
            date.monthNumber.toString().padStart(2, '0')
        }.${date.year}"
    }

    fun Long.toReadableDate(): String {
        val instant = Instant.fromEpochMilliseconds(this)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        val day = localDateTime.dayOfMonth.toString().padStart(2, '0')
        val month =
            when (localDateTime.month.number) {
                1 -> "Jan"
                2 -> "Feb"
                3 -> "Mar"
                4 -> "Apr"
                5 -> "May"
                6 -> "Jun"
                7 -> "Jul"
                8 -> "Aug"
                9 -> "Sep"
                10 -> "Oct"
                11 -> "Nov"
                12 -> "Dec"
                else -> ""
            }

        return "$day $month"
    }

    fun String?.toReadableDateOrEmpty(): String {
        if (this == null) return ""
        return try {
            val localDate = kotlinx.datetime.LocalDate.parse(this.substringBefore("T"))
            val day = localDate.dayOfMonth.toString().padStart(2, '0')
            val month =
                when (localDate.month.number) {
                    1 -> "Jan"
                    2 -> "Feb"
                    3 -> "Mar"
                    4 -> "Apr"
                    5 -> "May"
                    6 -> "Jun"
                    7 -> "Jul"
                    8 -> "Aug"
                    9 -> "Sep"
                    10 -> "Oct"
                    11 -> "Nov"
                    12 -> "Dec"
                    else -> ""
                }
            "$day $month"
        } catch (_: Exception) {
            this
        }
    }

    fun isoToEpochMillis(isoDate: String?): Long {
        if (isoDate == null) return 0L
        return try {
            val localDate = kotlinx.datetime.LocalDate.parse(isoDate.substringBefore("T"))
            val startOfDay = localDate.atStartOfDayIn(TimeZone.currentSystemDefault())
            startOfDay.toEpochMilliseconds()
        } catch (_: Exception) {
            0L
        }
    }

    fun formatDateIso(isoDate: String?): String {
        if (isoDate == null) return ""
        return try {
            val localDate = kotlinx.datetime.LocalDate.parse(isoDate.substringBefore("T"))
            "${localDate.dayOfMonth.toString().padStart(2, '0')}.${
                localDate.monthNumber.toString().padStart(2, '0')
            }.${localDate.year}"
        } catch (_: Exception) {
            isoDate
        }
    }
}
