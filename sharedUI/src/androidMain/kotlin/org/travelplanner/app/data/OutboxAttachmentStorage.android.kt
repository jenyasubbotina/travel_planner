package org.travelplanner.app.data

import android.content.Context
import java.io.File

actual class OutboxAttachmentStorage(private val context: Context) {
    private val rootDir: File
        get() = File(context.filesDir, "outbox/attachments").also { it.mkdirs() }

    actual fun write(attachmentId: String, bytes: ByteArray): String {
        val file = File(rootDir, attachmentId)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file.absolutePath
    }

    actual fun read(absolutePath: String): ByteArray? {
        val file = File(absolutePath)
        return if (file.exists()) file.readBytes() else null
    }

    actual fun delete(absolutePath: String) {
        runCatching { File(absolutePath).delete() }
    }

    actual fun deleteByAttachmentId(attachmentId: String) {
        runCatching { File(rootDir, attachmentId).delete() }
    }
}
