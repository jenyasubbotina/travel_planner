package org.travelplanner.app.core.auth

import io.github.xxfast.kstore.KStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class AuthRequestException(
    val statusCode: Int,
    val body: String,
) : Exception("Auth request failed: $statusCode — $body")

class AuthTokenManager(
    private val sessionStore: KStore<AuthSession>,
    private val accountsStore: KStore<List<AuthSession>>,
    private val json: Json,
    private val baseUrlProvider: () -> String,
) {
    private val _session = MutableStateFlow<AuthSession?>(null)
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    private val _savedAccounts = MutableStateFlow<List<AuthSession>>(emptyList())
    val savedAccounts: StateFlow<List<AuthSession>> = _savedAccounts.asStateFlow()

    val accessToken: String? get() = _session.value?.accessToken
    val userId: String? get() = _session.value?.userId
    val isLoggedIn: Boolean get() = _session.value != null

    private val refreshMutex = Mutex()

    private val authClient: HttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    suspend fun loadSession() {
        _session.value = sessionStore.get()
    }

    suspend fun loadAccounts() {
        _savedAccounts.value = accountsStore.get() ?: emptyList()
    }

    suspend fun switchAccount(userId: String) {
        val account = _savedAccounts.value.find { it.userId == userId } ?: return
        saveSession(account)
    }

    suspend fun register(
        email: String,
        displayName: String,
        password: String,
    ): AuthSession {
        val response =
            authClient.post("${baseUrlProvider()}/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(email, displayName, password))
            }
        val status = response.status
        if (!status.isSuccess()) {
            val errorText = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            println("[auth] register failed: HTTP ${status.value} $errorText")
            throw AuthRequestException(status.value, errorText)
        }
        val authResponse: AuthResponse = response.body()
        val session = authResponse.toSession()
        saveSession(session)
        return session
    }

    suspend fun login(
        email: String,
        password: String,
    ): AuthSession {
        val response =
            authClient.post("${baseUrlProvider()}/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }
        val status = response.status
        if (!status.isSuccess()) {
            val errorText = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            println("[auth] login failed: HTTP ${status.value} $errorText")
            throw AuthRequestException(status.value, errorText)
        }
        val authResponse: AuthResponse = response.body()
        val session = authResponse.toSession()
        saveSession(session)
        return session
    }

    suspend fun registerOrLogin(
        email: String,
        displayName: String,
        password: String,
    ): AuthSession =
        try {
            register(email, displayName, password)
        } catch (e: AuthRequestException) {
            if (e.statusCode == 409) {
                login(email, password)
            } else {
                throw e
            }
        }

    suspend fun refreshAccessToken(): String? =
        refreshMutex.withLock {
            val currentSession = _session.value ?: return@withLock null
            try {
                val response =
                    authClient.post("${baseUrlProvider()}/api/v1/auth/refresh") {
                        contentType(ContentType.Application.Json)
                        setBody(RefreshTokenRequest(currentSession.refreshToken))
                    }
                val status = response.status
                if (!status.isSuccess()) {
                    println("[auth] refresh failed: HTTP ${status.value}")
                    if (status.value == 401 || status.value == 403) {
                        clearSession()
                    }
                    return@withLock null
                }
                val refreshResponse: RefreshTokenResponse = response.body()
                val updated =
                    currentSession.copy(
                        accessToken = refreshResponse.accessToken,
                        refreshToken = refreshResponse.refreshToken,
                    )
                saveSession(updated)
                updated.accessToken
            } catch (e: Exception) {
                println("[auth] refresh exception: ${e::class.simpleName}: ${e.message}")
                null
            }
        }

    suspend fun logout() {
        try {
            authClient.post("${baseUrlProvider()}/api/v1/auth/logout") {
                contentType(ContentType.Application.Json)
                val token = _session.value?.accessToken
                if (token != null) header("Authorization", "Bearer $token")
            }
        } catch (_: Exception) {
        }
        clearSession()
    }

    private suspend fun saveSession(session: AuthSession) {
        sessionStore.set(session)
        _session.value = session

        accountsStore.update { current ->
            val list = current ?: emptyList()
            list.filter { it.userId != session.userId } + session
        }
        _savedAccounts.value = accountsStore.get() ?: emptyList()
    }

    private suspend fun clearSession() {
        sessionStore.set(null)
        _session.value = null
    }

    private fun AuthResponse.toSession() =
        AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = user.id,
            email = user.email,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
        )
}
