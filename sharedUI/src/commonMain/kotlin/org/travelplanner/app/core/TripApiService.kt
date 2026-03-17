package org.travelplanner.app.core

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.travelplanner.app.features.tripDetails.history.ConflictException
import org.travelplanner.app.features.tripDetails.history.data.HistoryLogDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventDto
import kotlin.coroutines.cancellation.CancellationException

class TripApiService(
    private val userSession: UserSession,
    private val json: Json,
    private val globalNotifier: GlobalNotifier,
    private val gateway: GatewayConfigManager,
) {
    var isOffline: Boolean = true

    private val client =
        HttpClient {
            install(ContentNegotiation) { json(json) }
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
                pingIntervalMillis = 15_000
            }

            defaultRequest {
                val currentUserId = userSession.currentUser.value?.id
                if (currentUserId != null) {
                    header("X-User-Id", currentUserId.toString())
                }

                if (this.url.protocol.name != "ws" && this.url.protocol.name != "wss") {
                    contentType(ContentType.Application.Json)
                }
            }
        }.also { httpClient ->
            httpClient.plugin(HttpSend).intercept { request ->
                val protocol = request.url.protocol.name
                if (protocol == "ws" || protocol == "wss") {
                    return@intercept execute(request)
                }

                val method = request.method
                val isMutation =
                    method == HttpMethod.Post ||
                        method == HttpMethod.Put ||
                        method == HttpMethod.Delete ||
                        method == HttpMethod.Patch

                if (isOffline && isMutation) {
                    globalNotifier.notifyError("Нет сети. Доступен только режим чтения.")
                    throw CancellationException("Offline mode active - mutation blocked")
                }

                if (isOffline) {
                    throw CancellationException("Offline mode active - skipping fetch silently")
                }

                try {
                    val call = execute(request)

                    val status = call.response.status
                    if (!status.isSuccess()) {

                        if (status.value == 409) {
                            throw ConflictException(call.response.bodyAsText())
                        }

                        val errorText = call.response.bodyAsText()
                        println("API ERROR: $status - $errorText")

                        throw Exception("HTTP Error ${status.value}: $errorText")
                    }

                    return@intercept call
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ConflictException) {
                    throw e
                } catch (e: Exception) {
                    if (isMutation) {
                        if (e.message?.contains("HTTP Error 400") == true) {
                            globalNotifier.notifyError("Ошибка данных. Проверьте введенные значения.")
                        } else {
                            globalNotifier.notifyError("Ошибка сети. Изменения не сохранены.")
                        }
                    } else {
                        globalNotifier.notifyError("Ошибка загрузки данных.")
                    }

                    throw CancellationException("Safe abort due to: ${e.message}")
                }
            }
        }

    private val baseUrl: String get() = gateway.baseUrl
    private val wsUrl: String get() = gateway.wsUrl

    fun resolveUrl(path: String?): String? = path?.let { "$baseUrl$it" }

    fun connectToGlobalEvents(onConnect: () -> Unit): Flow<TripEvent> =
        channelFlow {
            println("WS-DEBUG: Connecting to global events...")
            try {
                client.webSocket("$wsUrl/ws/events") {
                    println("WS-DEBUG: HANDSHAKE SUCCESS! Global socket open.")

                    onConnect()

                    while (true) {
                        val event = receiveDeserialized<TripEvent>()
                        println("WS-DEBUG: Received global event: $event")
                        send(event)
                    }
                }
            } catch (e: Exception) {
                println("WS-DEBUG: WEBSOCKET DISCONNECTED: ${e.message}")
                close(e)
            }
        }

    suspend fun getUserTrips(): List<TripDto> = client.get("$baseUrl/trips").body()

    suspend fun createTrip(request: CreateTripRequest): TripDto {
        val response =
            client.post("$baseUrl/trips") {
                setBody(request)
            }
        println("TRIP_DEBUG 📡 HTTP Status: ${response.status}")
        println("TRIP_DEBUG 📡 Raw body: ${response.bodyAsText()}")
        return response.body()
    }

    suspend fun uploadPhoto(photoBytes: ByteArray): String {
        @Serializable
        data class UploadResponse(
            val url: String,
        )

        val response =
            client.post("$baseUrl/upload") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "image",
                                photoBytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "image/jpeg")
                                    append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                                },
                            )
                        },
                    ),
                )
            }
        return response.body<UploadResponse>().url
    }

    suspend fun uploadFile(
        fileBytes: ByteArray,
        fileName: String,
    ): String {
        @Serializable
        data class UploadResponse(
            val url: String,
        )

        val response =
            client.post("$baseUrl/upload") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "image",
                                fileBytes,
                                Headers.build {
                                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                },
                            )
                        },
                    ),
                )
            }
        return response.body<UploadResponse>().url
    }

    suspend fun deleteOrLeaveTrip(tripId: Long) {
        client.delete("$baseUrl/trips/$tripId")
    }

    suspend fun setTripStatus(
        tripId: Long,
        status: String,
    ) {
        client.put("$baseUrl/trips/$tripId/status?status=$status")
    }

    suspend fun updateTripBudget(
        tripId: Long,
        newBudget: Double,
    ): TripDto =
        client
            .put("$baseUrl/trips/$tripId/budget") {
                contentType(ContentType.Application.Json)
                setBody(UpdateBudgetRequest(newBudget))
            }.body()

    suspend fun joinTrip(
        tripId: Long,
        userId: String,
        name: String,
    ) {
        client.post("$baseUrl/trips/$tripId/participants") {
            contentType(ContentType.Application.Json)
            setBody(JoinTripRequest(userId, name))
        }
    }

    suspend fun requestJoinTrip(
        code: String,
        userId: String,
        name: String,
    ): TripDto {
        val response =
            client.post("$baseUrl/trips/join-request") {
                contentType(ContentType.Application.Json)
                setBody(JoinByCodeRequest(code, userId, name))
            }

        if (response.status.value in 200..299) {
            return response.body()
        } else {
            throw Exception("Failed to join trip: ${response.status}")
        }
    }

    suspend fun updateTripFiles(
        tripId: Long,
        filesJson: String,
    ) {
        @Serializable
        data class UpdateFilesRequest(
            val filesJson: String,
        )

        client.put("$baseUrl/trips/$tripId/files") {
            contentType(ContentType.Application.Json)
            setBody(UpdateFilesRequest(filesJson))
        }
    }

    suspend fun getPendingRequests(tripId: Long): List<UserDto> = client.get("$baseUrl/trips/$tripId/requests").body()

    suspend fun resolveRequest(
        tripId: Long,
        userId: String,
        approve: Boolean,
    ) {
        client.post("$baseUrl/trips/$tripId/requests/$userId/resolve?approve=$approve")
    }

    suspend fun regenerateCode(tripId: Long): String {
        @Serializable
        data class CodeResponse(
            val newCode: String,
        )

        val response = client.post("$baseUrl/trips/$tripId/regenerate-code").body<CodeResponse>()
        return response.newCode
    }

    suspend fun getParticipants(tripId: Long): List<UserDto> = client.get("$baseUrl/trips/$tripId/participants").body()

    suspend fun getExpenses(tripId: Long): List<ExpenseDto> = client.get("$baseUrl/trips/$tripId/expenses").body()

    suspend fun addExpense(
        tripId: Long,
        expense: CreateExpenseRequest,
    ): ExpenseDto =
        client
            .post("$baseUrl/trips/$tripId/expenses") {
                setBody(expense)
                header("Content-Type", "application/json")
            }.body()

    suspend fun updateExpense(
        tripId: Long,
        expenseRemoteId: String,
        expense: CreateExpenseRequest,
        baseHash: String,
        force: Boolean = false,
    ): ExpenseDto {
        val response =
            client.put("$baseUrl/trips/$tripId/expenses/$expenseRemoteId?baseHash=$baseHash&force=$force") {
                contentType(ContentType.Application.Json)
                setBody(expense)
            }

        if (response.status == HttpStatusCode.Conflict) {
            val serverState = response.bodyAsText()
            throw ConflictException(serverState)
        }

        if (response.status.value !in 200..299) {
            throw Exception("HTTP Error: ${response.status}")
        }

        return response.body()
    }

    suspend fun deleteExpense(
        tripId: Long,
        expenseRemoteId: String,
    ) {
        client.delete("$baseUrl/trips/$tripId/expenses/$expenseRemoteId")
    }

    suspend fun resolveConflict(
        tripId: Long,
        expenseRemoteId: String,
        accept: Boolean,
    ): ExpenseDto =
        client
            .post("$baseUrl/trips/$tripId/expenses/$expenseRemoteId/resolve?accept=$accept")
            .body()

    suspend fun getEvents(tripId: Long): List<EventDto> = client.get("$baseUrl/trips/$tripId/events").body()

    suspend fun addEvent(
        tripId: Long,
        event: EventDto,
    ): EventDto =
        client
            .post("$baseUrl/trips/$tripId/events") {
                setBody(event)
                header("Content-Type", "application/json")
            }.body()

    suspend fun updateEvent(
        tripId: Long,
        event: EventDto,
    ): EventDto =
        client
            .put("$baseUrl/trips/$tripId/events/${event.id}") {
                setBody(event)
                header("Content-Type", "application/json")
            }.body()

    suspend fun deleteEvent(
        tripId: Long,
        eventId: String,
    ) {
        client.delete("$baseUrl/trips/$tripId/events/$eventId")
    }

    suspend fun getTripHistory(tripId: Long): List<HistoryLogDto> = client.get("$baseUrl/trips/$tripId/history").body()

    suspend fun getChecklist(tripId: Long): List<ChecklistItemDto> = client.get("$baseUrl/trips/$tripId/checklist").body()

    suspend fun addChecklistItem(
        tripId: Long,
        request: CreateChecklistItemRequest,
    ): ChecklistItemDto = client.post("$baseUrl/trips/$tripId/checklist") { setBody(request) }.body()

    suspend fun toggleChecklistItem(
        tripId: Long,
        itemId: String,
    ): ChecklistItemDto = client.put("$baseUrl/trips/$tripId/checklist/$itemId/toggle").body()

    suspend fun deleteChecklistItem(
        tripId: Long,
        itemId: String,
    ) {
        client.delete("$baseUrl/trips/$tripId/checklist/$itemId")
    }
}
