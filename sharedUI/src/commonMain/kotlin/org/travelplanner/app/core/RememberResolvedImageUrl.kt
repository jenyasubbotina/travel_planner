package org.travelplanner.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import org.travelplanner.app.data.TripRepository

@Composable
fun rememberResolvedImageUrl(s3KeyOrUrl: String?): String? {
    if (s3KeyOrUrl.isNullOrBlank()) return null
    if (s3KeyOrUrl.startsWith("http://") || s3KeyOrUrl.startsWith("https://")) {
        return s3KeyOrUrl
    }
    val repo: TripRepository = koinInject()
    var resolved by remember(s3KeyOrUrl) { mutableStateOf<String?>(null) }
    LaunchedEffect(s3KeyOrUrl) {
        resolved = runCatching { repo.getDownloadUrl(s3KeyOrUrl) }.getOrNull()
    }
    return resolved
}
