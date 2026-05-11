package org.travelplanner.app.core

import kotlin.time.TimeSource

object NetworkDebugLogger {
    private val isEnabled: Boolean by lazy { isNetworkDebugLoggingEnabled() }

    fun isActive(): Boolean = isEnabled

    fun logRaw(tag: String, message: String) {
        if (!isEnabled) return
        println("[net][$tag] $message")
    }

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
