package org.travelplanner.app.core

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFileDownloader(): (url: String, fileName: String) -> Unit =
    { _, _ ->
        println("STUB: Desktop FileDownloader")
    }
