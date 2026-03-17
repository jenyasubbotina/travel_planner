package org.travelplanner.app.core

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

object TripUtils {
    fun generateRandomColor(): String {
        val chars = "0123456789ABCDEF"
        return "#" + (1..6).map { chars.random() }.joinToString("")
    }

    fun formatDateTime(millis: Long): String {
        val instant = Instant.fromEpochMilliseconds(millis)
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val month =
            when (dt.monthNumber) {
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
        return "${dt.dayOfMonth} $month, ${dt.hour}:${dt.minute.toString().padStart(2, '0')}"
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
}
