package org.travelplanner.app.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun documentsDirectory(): String {
    val url =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: error("Documents directory not found")
    return requireNotNull(url.path)
}

@OptIn(ExperimentalForeignApi::class)
actual class OutboxAttachmentStorage {
    private fun rootPath(): String = "${documentsDirectory()}/outbox/attachments"

    private fun filePath(attachmentId: String): String = "${rootPath()}/$attachmentId"

    private fun ensureRoot() {
        SystemFileSystem.createDirectories(Path(rootPath()), mustCreate = false)
    }

    actual fun write(attachmentId: String, bytes: ByteArray): String {
        ensureRoot()
        val path = Path(filePath(attachmentId))
        SystemFileSystem.sink(path, append = false).buffered().use { sink ->
            sink.write(bytes)
        }
        return path.toString()
    }

    actual fun read(absolutePath: String): ByteArray? {
        val path = Path(absolutePath)
        if (!SystemFileSystem.exists(path)) return null
        return SystemFileSystem.source(path).buffered().use { it.readByteArray() }
    }

    actual fun delete(absolutePath: String) {
        runCatching {
            SystemFileSystem.delete(Path(absolutePath), mustExist = false)
        }
    }

    actual fun deleteByAttachmentId(attachmentId: String) {
        runCatching {
            SystemFileSystem.delete(Path(filePath(attachmentId)), mustExist = false)
        }
    }
}
