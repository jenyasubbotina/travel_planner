package org.travelplanner.app.core

@Suppress("SwallowedException")
internal actual fun isNetworkDebugLoggingEnabled(): Boolean =
    runCatching {
        val clazz = Class.forName("org.travelplanner.app.androidApp.BuildConfig")
        clazz.getField("DEBUG").getBoolean(null)
    }.getOrDefault(false)
