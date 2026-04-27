package org.travelplanner.app.androidApp

import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.azikar24.wormaceptor.api.Feature
import com.azikar24.wormaceptor.api.WormaCeptorApi
import com.azikar24.wormaceptor.api.ktor.WormaCeptorKtorPlugin
import com.google.firebase.messaging.FirebaseMessaging
import com.yandex.mapkit.MapKitFactory
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import io.ktor.client.HttpClientConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.files.Path
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.travelplanner.app.androidApp.BuildConfig
import org.travelplanner.app.core.DriverFactory
import org.travelplanner.app.core.GatewayConfig
import org.travelplanner.app.core.GatewayConfigManager
import org.travelplanner.app.core.RegisterDeviceRequest
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.auth.AuthSession
import org.travelplanner.app.core.auth.AuthTokenManager
import org.travelplanner.app.core.commonModule
import org.travelplanner.app.data.GlobalSyncManager
import org.travelplanner.app.data.NetworkState

class TripApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPKIT_API_KEY)
        MapKitFactory.setLocale("ru_RU")
        MapKitFactory.initialize(this)

        val tripInvitesChannel =
            NotificationChannelCompat
                .Builder(
                    "trip_invites",
                    NotificationManagerCompat.IMPORTANCE_HIGH,
                ).setName("Trip invitations")
                .setDescription("Notifies you when you're invited to a trip")
                .build()
        NotificationManagerCompat.from(this).createNotificationChannel(tripInvitesChannel)

        startKoin {
            androidContext(this@TripApplication)

            modules(
                commonModule,
                androidModule,
            )
        }

        WormaCeptorApi.init(
            context = this,
            features = Feature.ALL - Feature.MEMORY_MONITOR,
        )

        val globalSyncManager: GlobalSyncManager = get()
        val api: TripApiService = get()
        val authTokenManager: AuthTokenManager = get()

        CoroutineScope(Dispatchers.IO).launch {
            authTokenManager.session
                .map { it?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId == null) return@collect
                    try {
                        globalSyncManager.networkState.first { it == NetworkState.ONLINE }
                        val token = fetchFcmToken() ?: return@collect
                        val device =
                            api.registerDevice(
                                RegisterDeviceRequest(
                                    fcmToken = token,
                                    deviceName = android.os.Build.MODEL,
                                ),
                            )
                        api.currentDeviceId = device.id
                        println("[FCM] Registered device ${device.id} for userId=$userId")
                    } catch (e: Exception) {
                        println("[FCM] Registration failed for userId=$userId: ${e::class.simpleName}: ${e.message}")
                    }
                }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    globalSyncManager.onAppResumed()
                }
            },
        )
    }

    private suspend fun fetchFcmToken(): String? =
        suspendCancellableCoroutine { cont ->
            FirebaseMessaging
                .getInstance()
                .token
                .addOnSuccessListener { token ->
                    if (cont.isActive) cont.resumeWith(Result.success(token))
                }.addOnFailureListener { _ ->
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
        }
}

val androidModule =
    module {
        single { DriverFactory(androidContext()).createDriver() }

        single<(HttpClientConfig<*>.() -> Unit)> {
            {
                install(WormaCeptorKtorPlugin) {
                    maxContentLength = 500_000L
                    redactHeader("Authorization")
                    redactHeader("Cookie")
                }
            }
        }

        single<KStore<AuthSession>> {
            val ctx = androidContext()
            val file = Path("${ctx.filesDir.path}/auth_session.json")
            storeOf(
                file = file,
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
            val ctx = androidContext()
            val file = Path("${ctx.filesDir.path}/saved_accounts.json")
            storeOf(file = file, default = emptyList())
        }

        single {
            val ctx = androidContext()
            val file = Path("${ctx.filesDir.path}/gateway.json")
            GatewayConfigManager(storeOf(file = file, default = GatewayConfig()))
        }
    }
