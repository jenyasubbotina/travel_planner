package org.travelplanner.app.core

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

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
            val downloadId = downloadManager.enqueue(request)
            println("[download] enqueued id=$downloadId file=$fileName url=$url")

            Thread {
                repeat(10) {
                    SystemClock.sleep(1500)
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    downloadManager.query(query)?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        val downloadedSoFar =
                            cursor.getLong(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                            )
                        val totalBytes =
                            cursor.getLong(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                            )
                        val uri =
                            runCatching {
                                cursor.getString(
                                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI),
                                )
                            }.getOrNull()
                        println(
                            "[download] id=$downloadId status=$status reason=$reason progress=$downloadedSoFar/$totalBytes uri=$uri",
                        )
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            return@Thread
                        }
                    }
                }
            }.start()
        }
    }
}
