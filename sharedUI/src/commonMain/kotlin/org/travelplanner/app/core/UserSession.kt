package org.travelplanner.app.core

import kotlinx.coroutines.CoroutineScope
import org.travelplanner.app.AppBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.travelplanner.app.core.auth.AuthTokenManager

data class RegistrationPending(
    val serverMessage: String,
    val email: String,
)

@Serializable
data class AppUser(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
)

class UserSession(
    private val authTokenManager: AuthTokenManager,
) {
    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _availableUsers = MutableStateFlow<List<AppUser>>(emptyList())
    val availableUsers = _availableUsers.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded = _isLoaded.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating = _isAuthenticating.asStateFlow()

    private val _registrationPending = MutableStateFlow<RegistrationPending?>(null)
    val registrationPending = _registrationPending.asStateFlow()

    init {
        CoroutineScope(AppBackground).launch {
            authTokenManager.loadAccounts()
            authTokenManager.loadSession()
            val session = authTokenManager.session.value
            if (session != null) {
                _currentUser.value =
                    AppUser(
                        id = session.userId,
                        name = session.displayName,
                        email = session.email,
                        avatarUrl = session.avatarUrl,
                    )
            }
            _isLoaded.value = true
        }

        CoroutineScope(AppBackground).launch {
            authTokenManager.session.collect { session ->
                _currentUser.value =
                    session?.let {
                        AppUser(
                            id = it.userId,
                            name = it.displayName,
                            email = it.email,
                            avatarUrl = it.avatarUrl,
                        )
                    }
            }
        }

        CoroutineScope(AppBackground).launch {
            authTokenManager.savedAccounts.collect { accounts ->
                _availableUsers.value =
                    accounts.map {
                        AppUser(
                            id = it.userId,
                            name = it.displayName,
                            email = it.email,
                            avatarUrl = it.avatarUrl,
                        )
                    }
            }
        }
    }

    suspend fun register(
        email: String,
        displayName: String,
        password: String,
    ) {
        _authError.value = null
        _registrationPending.value = null
        _isAuthenticating.value = true
        try {
            val pending = authTokenManager.register(email, displayName, password)
            _registrationPending.value =
                RegistrationPending(
                    serverMessage = pending.message,
                    email = pending.user.email,
                )
        } catch (e: Exception) {
            println("[auth] register failed: ${e::class.simpleName}: ${e.message}")
            _authError.value = humanizeAuthError(e)
        } finally {
            _isAuthenticating.value = false
        }
    }

    suspend fun login(
        email: String,
        displayName: String,
        password: String,
    ) = login(email, password)

    suspend fun login(
        email: String,
        password: String,
    ) {
        _authError.value = null
        _isAuthenticating.value = true
        try {
            authTokenManager.login(email, password)
        } catch (e: Exception) {
            println("[auth] login failed: ${e::class.simpleName}: ${e.message}")
            _authError.value = humanizeAuthError(e)
        } finally {
            _isAuthenticating.value = false
        }
    }

    private fun humanizeAuthError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            "INVALID_CREDENTIALS" in msg -> "Неверный email или пароль"
            "EMAIL_NOT_VERIFIED" in msg ->
                "Подтвердите email: откройте ссылку из письма, затем войдите снова"
            "EMAIL_ALREADY_EXISTS" in msg -> "Этот email уже зарегистрирован"
            "VALIDATION_ERROR" in msg -> "Проверьте корректность данных"
            "INVALID_REFRESH_TOKEN" in msg -> "Сессия истекла, войдите заново"
            "TOKEN_EXPIRED" in msg -> "Сессия истекла, войдите заново"
            msg.isBlank() -> "Не удалось подключиться к серверу"
            else -> msg
        }
    }

    fun switchUser(userId: String) {
        CoroutineScope(AppBackground).launch {
            _authError.value = null
            _isAuthenticating.value = true
            try {
                authTokenManager.switchAccount(userId)
            } catch (e: Exception) {
                println("[auth] quick login failed: ${e::class.simpleName}: ${e.message}")
                _authError.value = humanizeAuthError(e)
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    suspend fun logout() {
        authTokenManager.logout()
    }

    fun clearError() {
        _authError.value = null
    }

    fun clearRegistrationPending() {
        _registrationPending.value = null
    }
}
