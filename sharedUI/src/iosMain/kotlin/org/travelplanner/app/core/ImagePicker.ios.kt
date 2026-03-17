package org.travelplanner.app.core

import androidx.compose.runtime.Composable

@Composable
actual fun ImagePicker(
    onImagePicked: (ByteArray?) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit
) {
    content {
        println("STUB: iOS ImagePicker clicked")
        onImagePicked(null)
    }
}