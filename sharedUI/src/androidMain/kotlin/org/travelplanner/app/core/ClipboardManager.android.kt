package org.travelplanner.app.core

import android.content.ClipData
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberClipboardManager(): ClipboardManagerHelper {
    val context = LocalContext.current
    return ClipboardManagerHelper(context)
}

actual class ClipboardManagerHelper(
    private val context: Context,
) {
    actual fun copyToClipboard(text: String) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("address", text))
    }
}
