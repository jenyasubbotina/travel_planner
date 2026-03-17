package org.travelplanner.app.core

import io.github.xxfast.kstore.KStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class AppUser(
    val id: Long,
    val name: String,
    val email: String,
)

class UserSession(
    private val store: KStore<List<AppUser>>,
) {
    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _availableUsers = MutableStateFlow<List<AppUser>>(emptyList())
    val availableUsers = _availableUsers.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            val users = store.get() ?: emptyList()
            _availableUsers.value = users
            if (users.isNotEmpty()) {
                _currentUser.value = users.last()
            }
            delay(1200)
            _isLoaded.value = true
        }
    }

    suspend fun createNewUser(name: String) {
        val newId = Random.nextLong(1000, 99999999)

        val newUser =
            AppUser(
                id = newId,
                name = name,
                email = "${name.lowercase().replace(" ", "")}@test.com",
            )

        store.update { currentList ->
            val list = currentList ?: emptyList()
            list + newUser
        }

        _availableUsers.value = (store.get() ?: emptyList())
        _currentUser.value = newUser
    }

    fun switchUser(userId: Long) {
        val user = _availableUsers.value.find { it.id == userId }
        if (user != null) {
            _currentUser.value = user
        }
    }

    fun logout() {
        _currentUser.value = null
    }
}
