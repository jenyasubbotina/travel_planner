package org.travelplanner.app.core

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFileDownloader(): (url: String, fileName: String) -> Unit
