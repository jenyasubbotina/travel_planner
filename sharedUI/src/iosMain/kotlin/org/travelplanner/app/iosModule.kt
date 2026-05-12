package org.travelplanner.app

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.travelplanner.app.core.DriverFactory
import org.travelplanner.app.core.GatewayConfig
import org.travelplanner.app.core.GatewayConfigManager
import org.travelplanner.app.core.auth.AuthSession
import org.travelplanner.app.core.commonModule
import org.travelplanner.app.core.preferences.AppPreferences
import org.travelplanner.app.data.BackgroundDrainScheduler
import org.travelplanner.app.data.OutboxAttachmentStorage
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun iosDocumentsPath(): String {
    val url =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: error("Documents directory not found")
    return requireNotNull(url.path)
}

val iosModule =
    module {
        single { DriverFactory().createDriver() }

        single<KStore<AuthSession>> {
            storeOf(
                file = Path("${iosDocumentsPath()}/auth_session.json"),
                default =
                    AuthSession(
                        accessToken = "",
                        refreshToken = "",
                        userId = "",
                        email = "",
                        displayName = "",
                    ),
            )
        }

        single<KStore<List<AuthSession>>>(named("accounts")) {
            storeOf(
                file = Path("${iosDocumentsPath()}/saved_accounts.json"),
                default = emptyList(),
            )
        }

        single<KStore<AppPreferences>>(named("appPrefs")) {
            storeOf(
                file = Path("${iosDocumentsPath()}/app_prefs.json"),
                default = AppPreferences(),
            )
        }

        single<String>(named("appVersion")) {
            NSBundle.mainBundle.infoDictionary
                ?.get("CFBundleShortVersionString") as? String
                ?: "1.0.0"
        }

        single {
            GatewayConfigManager(
                storeOf(
                    file = Path("${iosDocumentsPath()}/gateway.json"),
                    default = GatewayConfig(),
                ),
            )
        }

        single { BackgroundDrainScheduler() }

        single { OutboxAttachmentStorage() }
    }

private object IosKoin {
    var started: Boolean = false
}

fun ensureIosKoinStarted() {
    if (IosKoin.started) return
    IosKoin.started = true
    startKoin {
        modules(commonModule, iosModule)
    }
}
