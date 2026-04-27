package org.travelplanner.app.core

fun stripDurationSuffix(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return value
        .replace("ч.", "")
        .replace("ч", "")
        .replace(',', '.')
        .trim()
}

fun formatHoursDuration(rawNumber: String): String {
    val cleaned = rawNumber.trim().replace(',', '.')
    val n = cleaned.toDoubleOrNull() ?: return ""
    if (n <= 0.0) return ""
    val asInt = n.toInt()
    return if (n == asInt.toDouble()) "$asInt ч" else "$n ч"
}

private val russianMonthAbbr =
    arrayOf(
        "янв",
        "фев",
        "мар",
        "апр",
        "май",
        "июн",
        "июл",
        "авг",
        "сен",
        "окт",
        "ноя",
        "дек",
    )

fun formatRussianDateTime(iso: String): String {
    if (iso.isBlank()) return iso
    val datePart = iso.substringBefore('T')
    val timePart = iso.substringAfter('T', "").take(5).takeIf { it.length == 5 }

    val components = datePart.split('-')
    if (components.size != 3) return iso

    val month = components[1].toIntOrNull()?.let { it.takeIf { m -> m in 1..12 } } ?: return iso
    val day = components[2].toIntOrNull() ?: return iso

    val datePiece = "$day ${russianMonthAbbr[month - 1]}"
    return if (timePart != null) "$datePiece, $timePart" else datePiece
}
