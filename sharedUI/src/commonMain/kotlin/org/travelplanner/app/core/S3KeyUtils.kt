package org.travelplanner.app.core

private val uuidSegmentRegex =
    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
private val backendS3KeyRegex = Regex("^trips/$uuidSegmentRegex(?:/.*)?$")

fun isBackendS3Key(value: String): Boolean {
    val normalized = value.trim().substringBefore('?').substringBefore('#')
    return backendS3KeyRegex.matches(normalized)
}

fun extractBackendS3KeyOrNull(value: String): String? {
    val normalized = value.trim()
    if (normalized.isBlank()) return null

    val stripped = normalized.substringBefore('?').substringBefore('#')
    if (isBackendS3Key(stripped)) return stripped

    val markerIndex = stripped.indexOf("trips/")
    if (markerIndex < 0) return null

    val candidate = stripped.substring(markerIndex)
    return candidate.takeIf(::isBackendS3Key)
}
