package org.travelplanner.app.core

object Validation {
    const val TITLE_MAX = 255
    const val CURRENCY_MAX = 10
    const val DESCRIPTION_MAX = 4000
    const val ADDRESS_MAX = 500
    const val JOIN_CODE_MIN = 4
    const val JOIN_CODE_MAX = 16

    private val timeHhMmRegex = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
    private val uuidRegex =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val urlRegex = Regex("""^https?://[^\s]+$""", RegexOption.IGNORE_CASE)

    fun isValidTitle(s: String): Boolean = s.isNotBlank() && s.length <= TITLE_MAX

    fun isValidCurrency(s: String): Boolean = s.isNotBlank() && s.length in 1..CURRENCY_MAX

    fun isValidTimeHhMm(s: String): Boolean = s.isBlank() || timeHhMmRegex.matches(s)

    fun isPositiveAmount(s: String): Boolean = s.toDoubleOrNull()?.let { it > 0.0 } == true

    fun isNonNegativeAmount(s: String): Boolean = s.toDoubleOrNull()?.let { it >= 0.0 } == true

    fun isValidJoinCode(s: String): Boolean = s.isNotBlank() && s.length in JOIN_CODE_MIN..JOIN_CODE_MAX

    fun isValidUuid(s: String): Boolean = uuidRegex.matches(s)

    fun isValidUrl(s: String): Boolean = urlRegex.matches(s)
}
