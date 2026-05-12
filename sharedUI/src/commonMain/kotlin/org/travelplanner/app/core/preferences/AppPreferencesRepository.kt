package org.travelplanner.app.core.preferences

import io.github.xxfast.kstore.KStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.travelplanner.app.AppBackground

class AppPreferencesRepository(
    private val store: KStore<AppPreferences>,
) {
    private val _state = MutableStateFlow(AppPreferences())
    val state: StateFlow<AppPreferences> = _state.asStateFlow()

    val snapshot: AppPreferences get() = _state.value

    init {
        CoroutineScope(AppBackground).launch {
            _state.value = store.get() ?: AppPreferences()
        }
    }

    suspend fun setNotificationsEnabled(value: Boolean) = update { it.copy(notificationsEnabled = value) }

    suspend fun setLightTheme(value: Boolean) = update { it.copy(lightTheme = value) }

    private suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        val next = transform(_state.value)
        store.set(next)
        _state.value = next
    }
}
