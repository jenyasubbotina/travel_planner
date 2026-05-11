package org.travelplanner.app.data

actual class OutboxAttachmentStorage {
    private val root = mutableMapOf<String, ByteArray>()

    actual fun write(attachmentId: String, bytes: ByteArray): String {
        root[attachmentId] = bytes
        return "wasm://$attachmentId"
    }

    actual fun read(absolutePath: String): ByteArray? {
        val id = absolutePath.removePrefix("wasm://")
        return root[id]
    }

    actual fun delete(absolutePath: String) {
        val id = absolutePath.removePrefix("wasm://")
        root.remove(id)
    }

    actual fun deleteByAttachmentId(attachmentId: String) {
        root.remove(attachmentId)
    }
}
