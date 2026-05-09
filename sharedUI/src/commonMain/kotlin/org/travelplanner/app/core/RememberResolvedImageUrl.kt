package org.travelplanner.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import org.koin.compose.koinInject
import org.travelplanner.app.data.TripRepository

@Composable
fun rememberResolvedImageUrl(s3KeyOrUrl: String?): Any? {
    if (s3KeyOrUrl.isNullOrBlank()) return null
    if (s3KeyOrUrl.startsWith("http://") || s3KeyOrUrl.startsWith("https://")) {
        return s3KeyOrUrl
    }
    val context = LocalPlatformContext.current
    val objectKey = extractBackendS3KeyOrNull(s3KeyOrUrl) ?: return null
    val repo: TripRepository = koinInject()
    var url by remember(s3KeyOrUrl) { mutableStateOf<String?>(null) }
    LaunchedEffect(s3KeyOrUrl) {
        url = runCatching { repo.getDownloadUrl(objectKey) }.getOrNull()
    }
    return remember(s3KeyOrUrl, url, context) {
        ImageRequest
            .Builder(context)
            .data(url)
            .memoryCacheKey(s3KeyOrUrl)
            .diskCacheKey(s3KeyOrUrl)
            .build()
    }
}
