package org.travelplanner.app.data

actual class OutboxAttachmentStorage {
    actual fun write(attachmentId: String, bytes: ByteArray): String {
        error("OutboxAttachmentStorage is not implemented on iOS / native")
    }

    actual fun read(absolutePath: String): ByteArray? {
        error("OutboxAttachmentStorage is not implemented on iOS / native")
    }

    actual fun delete(absolutePath: String) {}

    actual fun deleteByAttachmentId(attachmentId: String) {}
}
