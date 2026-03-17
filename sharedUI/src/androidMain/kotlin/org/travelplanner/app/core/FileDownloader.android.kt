package org.travelplanner.app.core

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import android.net.Uri as AndroidUri

@Composable
actual fun rememberFileDownloader(): (url: String, fileName: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url: String, fileName: String ->
            val request =
                DownloadManager
                    .Request(url.toUri())
                    .setTitle(fileName)
                    .setDescription("Загрузка файла...")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)

            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
        }
    }
}
