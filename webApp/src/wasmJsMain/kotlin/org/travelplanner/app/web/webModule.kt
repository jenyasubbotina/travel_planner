package org.travelplanner.app.web

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.storage.storeOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.travelplanner.app.core.DriverFactory
import org.travelplanner.app.core.GatewayConfig
import org.travelplanner.app.core.GatewayConfigManager
import org.travelplanner.app.core.auth.AuthSession
import org.travelplanner.app.core.preferences.AppPreferences
import org.travelplanner.app.data.BackgroundDrainScheduler
import org.travelplanner.app.data.OutboxAttachmentStorage

val webModule =
    module {
        single { DriverFactory().createDriver() }

        single<KStore<AuthSession>> {
            storeOf(
                key = "travel_planner_auth_session",
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
            storeOf(key = "travel_planner_saved_accounts", default = emptyList())
        }

        single<KStore<AppPreferences>>(named("appPrefs")) {
            storeOf(key = "travel_planner_app_prefs", default = AppPreferences())
        }

        single<String>(named("appVersion")) { "1.0.0" }

        single {
            GatewayConfigManager(
                storeOf(
                    key = "travel_planner_gateway",
                    default = GatewayConfig(),
                ),
            )
        }

        single { BackgroundDrainScheduler() }

        single { OutboxAttachmentStorage() }
    }
