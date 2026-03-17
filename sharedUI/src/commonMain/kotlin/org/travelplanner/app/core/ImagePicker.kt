package org.travelplanner.app.core

import androidx.compose.runtime.Composable

@Composable
expect fun ImagePicker(
    onImagePicked: (ByteArray?) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit,
)
