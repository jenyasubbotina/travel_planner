package org.travelplanner.app.core

import androidx.compose.runtime.Composable
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
actual fun rememberClipboardManager(): ClipboardManagerHelper = ClipboardManagerHelper()

actual class ClipboardManagerHelper {
    actual fun copyToClipboard(text: String) {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    }
}
