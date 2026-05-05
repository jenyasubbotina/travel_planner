package org.travelplanner.app.data

import kotlinx.serialization.Serializable

@Serializable
data class AttachmentOutboxPayload(
    val attachmentId: String,
    val tripId: String,
    val expenseId: String? = null,
    val pointId: String? = null,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val localFilePath: String,
    val presignedS3Key: String? = null,
    val presignedUploadUrl: String? = null,
)
