package org.travelplanner.app.core

import kotlin.time.TimeSource

object NetworkDebugLogger {
    private val isEnabled: Boolean by lazy {
        runCatching {
            val clazz = Class.forName("org.travelplanner.app.androidApp.BuildConfig")
            clazz.getField("DEBUG").getBoolean(null)
        }.getOrDefault(false)
    }

    fun isActive(): Boolean = isEnabled

    fun start(tag: String, method: String, url: String): TimeSource.Monotonic.ValueTimeMark? {
        if (!isEnabled) return null
        println("[net][$tag] -> $method $url")
        return TimeSource.Monotonic.markNow()
    }

    fun success(
        tag: String,
        method: String,
        url: String,
        statusCode: Int,
        timer: TimeSource.Monotonic.ValueTimeMark?,
    ) {
        if (!isEnabled) return
        val elapsed = timer?.elapsedNow()?.inWholeMilliseconds
        println("[net][$tag] <- $statusCode $method $url (${elapsed ?: -1}ms)")
    }

    fun failure(
        tag: String,
        method: String,
        url: String,
        error: Throwable,
        timer: TimeSource.Monotonic.ValueTimeMark?,
    ) {
        if (!isEnabled) return
        val elapsed = timer?.elapsedNow()?.inWholeMilliseconds
        println("[net][$tag] xx $method $url (${elapsed ?: -1}ms): ${error::class.simpleName} ${error.message}")
    }
}
