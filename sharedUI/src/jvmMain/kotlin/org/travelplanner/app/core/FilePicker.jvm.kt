package org.travelplanner.app.core

import androidx.compose.runtime.Composable

@Composable
actual fun FilePicker(
    onFilePicked: (bytes: ByteArray?, fileName: String?) -> Unit,
    content: @Composable ((onClick: () -> Unit) -> Unit)
) {
    content {
        println("STUB: Desktop FilePicker clicked")
        onFilePicked(null, null)
    }
}