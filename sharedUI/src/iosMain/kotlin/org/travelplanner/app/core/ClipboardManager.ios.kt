package org.travelplanner.app.core

import androidx.compose.runtime.Composable
import platform.UIKit.UIPasteboard

@Composable
actual fun rememberClipboardManager(): ClipboardManagerHelper = ClipboardManagerHelper()

actual class ClipboardManagerHelper {
    actual fun copyToClipboard(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}
