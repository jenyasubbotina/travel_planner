package org.travelplanner.app.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BackendErrorResponse(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
    val traceId: String? = null,
)

class VersionConflictException(
    val error: BackendErrorResponse,
) : Exception("Version conflict: ${error.message}")

class PendingUpdateStoredException(
    val error: BackendErrorResponse,
) : Exception("Pending update stored: ${error.message}")

class AnotherPendingUpdateException(
    val error: BackendErrorResponse,
) : Exception("Another pending update exists: ${error.message}")

class BackendApiException(
    val statusCode: Int,
    val error: BackendErrorResponse,
) : Exception("Backend error $statusCode: ${error.message}")
