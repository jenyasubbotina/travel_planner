package org.travelplanner.app.core.auth

import io.github.xxfast.kstore.KStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
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
import org.travelplanner.app.core.NetworkDebugLogger

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
            if (NetworkDebugLogger.isActive()) {
                install(Logging) {
                    logger =
                        object : Logger {
                            override fun log(message: String) {
                                NetworkDebugLogger.logRaw(tag = "auth", message = message)
                            }
                        }
                    level = LogLevel.ALL
                    sanitizeHeader { header ->
                        header.equals("Authorization", ignoreCase = true) ||
                            header.equals("Cookie", ignoreCase = true) ||
                            header.equals("Set-Cookie", ignoreCase = true)
                    }
                }
            }
        }.also { httpClient ->
            httpClient.plugin(HttpSend).intercept { request ->
                val method = request.method.value
                val url = request.url.buildString()
                val timer = NetworkDebugLogger.start(tag = "auth", method = method, url = url)
                try {
                    val call = execute(request)
                    NetworkDebugLogger.success(
                        tag = "auth",
                        method = method,
                        url = url,
                        statusCode = call.response.status.value,
                        timer = timer,
                    )
                    call
                } catch (e: Exception) {
                    NetworkDebugLogger.failure(
                        tag = "auth",
                        method = method,
                        url = url,
                        error = e,
                        timer = timer,
                    )
                    throw e
                }
            }
        }
    }

    suspend fun loadSession() {
        _session.value = sessionStore.get()
    }

    suspend fun loadAccounts() {
        _savedAccounts.value = accountsStore.get() ?: emptyList()
    }

    suspend fun switchAccount(userId: String): AuthSession =
        refreshMutex.withLock {
            val account =
                _savedAccounts.value.find { it.userId == userId }
                    ?: throw AuthRequestException(0, "ACCOUNT_NOT_FOUND")

            val response =
                authClient.post("${baseUrlProvider()}/api/v1/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(account.refreshToken))
                }
            val status = response.status
            if (!status.isSuccess()) {
                val errorText = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
                println("[auth] quick login failed: HTTP ${status.value} $errorText")
                if (status.value == 401 || status.value == 403) {
                    removeAccount(userId)
                }
                throw AuthRequestException(status.value, errorText)
            }
            val refreshResponse: RefreshTokenResponse = response.body()
            val session =
                account.copy(
                    accessToken = refreshResponse.accessToken,
                    refreshToken = refreshResponse.refreshToken,
                )
            saveSession(session)
            session
        }

    suspend fun register(
        email: String,
        displayName: String,
        password: String,
    ): RegisterPendingResponse {
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
        return response.body()
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

    private suspend fun removeAccount(userId: String) {
        accountsStore.update { current ->
            (current ?: emptyList()).filter { it.userId != userId }
        }
        _savedAccounts.value = accountsStore.get() ?: emptyList()
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
