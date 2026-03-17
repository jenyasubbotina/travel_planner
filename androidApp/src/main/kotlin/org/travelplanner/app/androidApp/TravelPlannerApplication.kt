package org.travelplanner.app.androidApp

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.io.files.Path
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.travelplanner.app.core.AppUser
import org.travelplanner.app.core.DriverFactory
import org.travelplanner.app.core.GatewayConfig
import org.travelplanner.app.core.GatewayConfigManager
import org.travelplanner.app.core.commonModule
import org.travelplanner.app.data.GlobalSyncManager

class TripApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@TripApplication)

            modules(
                commonModule,
                androidModule,
            )
        }

        val globalSyncManager: GlobalSyncManager = get()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    globalSyncManager.onAppResumed()
                }
            },
        )
    }
}

val androidModule =
    module {
        single { DriverFactory(androidContext()).createDriver() }

        single<KStore<List<AppUser>>> {
            val ctx = androidContext()
            val file = Path("${ctx.filesDir.path}/users.json")
            storeOf(file = file, default = emptyList())
        }

        single {
            val ctx = androidContext()
            val file = Path("${ctx.filesDir.path}/gateway.json")
            GatewayConfigManager(storeOf(file = file, default = GatewayConfig()))
        }
    }
