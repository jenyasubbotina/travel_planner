package org.travelplanner.app.core

import androidx.compose.runtime.Composable

@Composable
expect fun rememberClipboardManager(): ClipboardManagerHelper

expect class ClipboardManagerHelper {
    fun copyToClipboard(text: String)
}
