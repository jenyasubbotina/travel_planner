package org.travelplanner.app.data


expect class OutboxAttachmentStorage {
    fun write(attachmentId: String, bytes: ByteArray): String

    fun read(absolutePath: String): ByteArray?

    fun delete(absolutePath: String)

    fun deleteByAttachmentId(attachmentId: String)
}
