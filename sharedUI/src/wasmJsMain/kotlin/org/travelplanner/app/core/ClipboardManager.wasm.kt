package org.travelplanner.app.core

import androidx.compose.runtime.Composable

@Composable
actual fun rememberClipboardManager(): ClipboardManagerHelper = ClipboardManagerHelper()

actual class ClipboardManagerHelper {
    actual fun copyToClipboard(text: String) {
        println("Web clipboard: $text")
    }
}
