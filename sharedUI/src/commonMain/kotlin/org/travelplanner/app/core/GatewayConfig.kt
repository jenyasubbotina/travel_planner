package org.travelplanner.app.core

import io.github.xxfast.kstore.KStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class GatewayConfig(
    val address: String = DEFAULT_ADDRESS,
    val useTls: Boolean = false,
) {
    val baseUrl: String get() = "${if (useTls) "https" else "http"}://$address"

    companion object {
        // const val DEFAULT_ADDRESS = "213.165.216.91:8080"
        const val DEFAULT_ADDRESS = "192.168.50.77:8080"
    }
}

class GatewayConfigManager(
    private val store: KStore<GatewayConfig>,
) {
    private val _config = MutableStateFlow(GatewayConfig())
    val config = _config.asStateFlow()

    val baseUrl: String get() = _config.value.baseUrl

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val saved = store.get()
            if (saved != null) {
                _config.value = saved
            }
        }
    }

    suspend fun updateConfig(config: GatewayConfig) {
        store.set(config)
        _config.value = config
    }
}
