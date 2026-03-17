package org.travelplanner.app.core

import androidx.compose.runtime.Composable

@Composable
expect fun FilePicker(
    onFilePicked: (bytes: ByteArray?, fileName: String?) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit
)