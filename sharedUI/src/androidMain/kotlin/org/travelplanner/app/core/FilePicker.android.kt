package org.travelplanner.app.core

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun FilePicker(
    onFilePicked: (ByteArray?, String?) -> Unit,
    content: @Composable (onClick: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                var fileName = "document.pdf"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
                    }
                }
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                onFilePicked(bytes, fileName)
            } else {
                onFilePicked(null, null)
            }
        }

    content {
        launcher.launch(arrayOf("application/pdf", "image/*"))
    }
}
