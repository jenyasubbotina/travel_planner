package org.travelplanner.app.core

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.travelplanner.app.core.auth.AuthTokenManager
import kotlin.coroutines.cancellation.CancellationException

class TripApiService(
    private val authTokenManager: AuthTokenManager,
    private val json: Json,
    private val globalNotifier: GlobalNotifier,
    private val gateway: GatewayConfigManager,
    private val httpClientConfig: (HttpClientConfig<*>.() -> Unit)? = null,
) {
    var isOffline: Boolean = true

    var currentDeviceId: String? = null

    private val plainClient = HttpClient()

    private val client =
        HttpClient {
            httpClientConfig?.invoke(this)
            install(ContentNegotiation) { json(json) }

            defaultRequest {
                val token = authTokenManager.accessToken
                if (token != null) {
                    header("Authorization", "Bearer $token")
                }
                contentType(ContentType.Application.Json)
            }
        }.also { httpClient ->
            httpClient.plugin(HttpSend).intercept { request ->
                val method = request.method
                val isMutation =
                    method == HttpMethod.Post ||
                        method == HttpMethod.Put ||
                        method == HttpMethod.Delete ||
                        method == HttpMethod.Patch

                val urlPath = request.url.buildString()
                val passThroughErrors =
                    urlPath.contains("/participants/invite") ||
                        urlPath.contains("/trip-invitations/")

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

                    if (status == HttpStatusCode.Unauthorized) {
                        val newToken = authTokenManager.refreshAccessToken()
                        if (newToken != null) {
                            request.headers.remove("Authorization")
                            request.headers.append("Authorization", "Bearer $newToken")
                            return@intercept execute(request)
                        }
                    }

                    if (!status.isSuccess()) {
                        val errorText = call.response.bodyAsText()
                        val backendError =
                            try {
                                json.decodeFromString<BackendErrorResponse>(errorText)
                            } catch (_: Exception) {
                                BackendErrorResponse(
                                    code = "UNKNOWN",
                                    message = errorText,
                                )
                            }

                        println(
                            "[TripApiService] ${request.method.value} $urlPath -> ${status.value} ${backendError.code}: ${backendError.message}",
                        )

                        if (status.value == 409) {
                            when (backendError.code) {
                                "PENDING_UPDATE_STORED" -> throw PendingUpdateStoredException(
                                    backendError,
                                )

                                "ANOTHER_PENDING_UPDATE" -> throw AnotherPendingUpdateException(
                                    backendError,
                                )

                                else -> throw VersionConflictException(backendError)
                            }
                        }

                        throw BackendApiException(status.value, backendError)
                    }

                    return@intercept call
                } catch (e: CancellationException) {
                    throw e
                } catch (e: VersionConflictException) {
                    throw e
                } catch (e: PendingUpdateStoredException) {
                    throw e
                } catch (e: AnotherPendingUpdateException) {
                    throw e
                } catch (e: BackendApiException) {
                    if (passThroughErrors) {
                        throw e
                    }
                    if (isMutation) {
                        if (e.statusCode == 400) {
                            globalNotifier.notifyError("Ошибка данных. Проверьте введенные значения.")
                        } else {
                            globalNotifier.notifyError("Ошибка сети. Изменения не сохранены.")
                        }
                    } else {
                        globalNotifier.notifyError("Ошибка загрузки данных.")
                    }
                    throw CancellationException("Safe abort due to: ${e.message}")
                } catch (e: Exception) {
                    println("[TripApiService] ${request.method.value} $urlPath failed: ${e::class.simpleName}: ${e.message}")
                    if (passThroughErrors) {
                        throw e
                    }
                    if (isMutation) {
                        globalNotifier.notifyError("Ошибка сети. Изменения не сохранены.")
                    } else {
                        globalNotifier.notifyError("Ошибка загрузки данных.")
                    }
                    throw CancellationException("Safe abort due to: ${e.message}")
                }
            }
        }

    private val baseUrl: String get() = gateway.baseUrl

    // -- Trips --

    suspend fun getTrips(): List<TripResponse> = client.get("$baseUrl/api/v1/trips").body()

    suspend fun createTrip(request: V2CreateTripRequest): TripResponse = client.post("$baseUrl/api/v1/trips") { setBody(request) }.body()

    suspend fun getTrip(tripId: String): TripResponse = client.get("$baseUrl/api/v1/trips/$tripId").body()

    suspend fun updateTrip(
        tripId: String,
        request: V2UpdateTripRequest,
    ): TripResponse = client.patch("$baseUrl/api/v1/trips/$tripId") { setBody(request) }.body()

    suspend fun deleteTrip(tripId: String) {
        client.delete("$baseUrl/api/v1/trips/$tripId")
    }

    suspend fun archiveTrip(tripId: String): TripResponse = client.post("$baseUrl/api/v1/trips/$tripId/archive").body()

    // -- Participants --

    suspend fun getParticipants(tripId: String): List<ParticipantDetailResponse> =
        client.get("$baseUrl/api/v1/trips/$tripId/participants").body()

    suspend fun inviteParticipant(
        tripId: String,
        request: InviteParticipantRequest,
    ): InvitationResponse = client.post("$baseUrl/api/v1/trips/$tripId/participants/invite") { setBody(request) }.body()

    suspend fun removeParticipant(
        tripId: String,
        userId: String,
    ) {
        client.delete("$baseUrl/api/v1/trips/$tripId/participants/$userId")
    }

    suspend fun changeRole(
        tripId: String,
        userId: String,
        request: ChangeRoleRequest,
    ) {
        client.patch("$baseUrl/api/v1/trips/$tripId/participants/$userId") { setBody(request) }
    }

    suspend fun acceptInvitation(invitationId: String): ParticipantResponse {
        val res: ParticipantResponse =
            client.post("$baseUrl/api/v1/trip-invitations/$invitationId/accept").body()
        return res
    }

    // -- Itinerary --

    suspend fun getItinerary(tripId: String): List<ItineraryPointResponse> = client.get("$baseUrl/api/v1/trips/$tripId/itinerary").body()

    suspend fun createItineraryPoint(
        tripId: String,
        request: V2CreateItineraryPointRequest,
    ): ItineraryPointResponse = client.post("$baseUrl/api/v1/trips/$tripId/itinerary") { setBody(request) }.body()

    suspend fun updateItineraryPoint(
        tripId: String,
        pointId: String,
        request: V2UpdateItineraryPointRequest,
    ): ItineraryPointResponse = client.patch("$baseUrl/api/v1/trips/$tripId/itinerary/$pointId") { setBody(request) }.body()

    suspend fun deleteItineraryPoint(
        tripId: String,
        pointId: String,
    ) {
        client.delete("$baseUrl/api/v1/trips/$tripId/itinerary/$pointId")
    }

    suspend fun reorderItinerary(
        tripId: String,
        request: ReorderRequest,
    ) {
        client.post("$baseUrl/api/v1/trips/$tripId/itinerary/reorder") { setBody(request) }
    }

    // -- Expenses --

    suspend fun getExpenses(tripId: String): List<ExpenseResponse> = client.get("$baseUrl/api/v1/trips/$tripId/expenses").body()

    suspend fun createExpense(
        tripId: String,
        request: V2CreateExpenseRequest,
    ): ExpenseResponse = client.post("$baseUrl/api/v1/trips/$tripId/expenses") { setBody(request) }.body()

    suspend fun getExpense(
        tripId: String,
        expenseId: String,
    ): ExpenseResponse = client.get("$baseUrl/api/v1/trips/$tripId/expenses/$expenseId").body()

    suspend fun updateExpense(
        tripId: String,
        expenseId: String,
        request: V2UpdateExpenseRequest,
    ): ExpenseResponse =
        client
            .patch("$baseUrl/api/v1/trips/$tripId/expenses/$expenseId") { setBody(request) }
            .body()

    suspend fun deleteExpense(
        tripId: String,
        expenseId: String,
    ) {
        client.delete("$baseUrl/api/v1/trips/$tripId/expenses/$expenseId")
    }

    // -- Analytics --

    suspend fun getBalances(tripId: String): List<BalanceResponse> = client.get("$baseUrl/api/v1/trips/$tripId/balances").body()

    suspend fun getSettlements(tripId: String): List<SettlementResponse> = client.get("$baseUrl/api/v1/trips/$tripId/settlements").body()

    suspend fun getStatistics(tripId: String): StatisticsResponse = client.get("$baseUrl/api/v1/trips/$tripId/statistics").body()

    // -- Attachments --

    suspend fun presignUpload(request: PresignUploadRequest): PresignedUploadResponse =
        client.post("$baseUrl/api/v1/attachments/presign") { setBody(request) }.body()

    suspend fun presignDownload(s3Key: String): PresignedDownloadResponse =
        client
            .post("$baseUrl/api/v1/attachments/presign-download") {
                setBody(PresignDownloadRequest(s3Key))
            }.body()

    suspend fun createAttachment(
        tripId: String,
        request: CreateAttachmentRequest,
    ): AttachmentResponse = client.post("$baseUrl/api/v1/trips/$tripId/attachments") { setBody(request) }.body()

    suspend fun createExpenseAttachment(
        tripId: String,
        expenseId: String,
        request: CreateAttachmentRequest,
    ): AttachmentResponse =
        client
            .post("$baseUrl/api/v1/trips/$tripId/expenses/$expenseId/attachments") {
                setBody(
                    request,
                )
            }.body()

    suspend fun deleteAttachment(attachmentId: String) {
        client.delete("$baseUrl/api/v1/attachments/$attachmentId")
    }

    suspend fun listTripFiles(
        tripId: String,
        scope: String = "trip",
    ): List<AttachmentResponse> = client.get("$baseUrl/api/v1/trips/$tripId/attachments?scope=$scope").body()

    suspend fun createPointAttachment(
        tripId: String,
        pointId: String,
        request: CreateAttachmentRequest,
    ): AttachmentResponse =
        client
            .post("$baseUrl/api/v1/trips/$tripId/itinerary/$pointId/attachments") {
                setBody(
                    request,
                )
            }.body()

    // -- Itinerary point links/comments --

    suspend fun getPointLinks(
        tripId: String,
        pointId: String,
    ): List<PointLinkResponse> {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return emptyList()
        return client.get("$baseUrl/api/v1/trips/$tripId/itinerary/$pointId/links").body()
    }

    suspend fun addPointLink(
        tripId: String,
        pointId: String,
        request: AddPointLinkRequest,
    ): PointLinkResponse? {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return null
        return client
            .post("$baseUrl/api/v1/trips/$tripId/itinerary/$pointId/links") {
                setBody(
                    request,
                )
            }.body()
    }

    suspend fun deletePointLink(
        tripId: String,
        pointId: String,
        linkId: String,
    ) {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return
        client.delete("$baseUrl/api/v1/trips/$tripId/itinerary/$pointId/links/$linkId")
    }

    suspend fun getPointComments(
        tripId: String,
        pointId: String,
        limit: Int = 100,
        offset: Int = 0,
    ): List<PointCommentResponse> {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return emptyList()
        return client
            .get("$baseUrl/api/v1/trips/$tripId/itinerary/$pointId/comments?limit=$limit&offset=$offset")
            .body()
    }

    suspend fun addPointComment(
        tripId: String,
        pointId: String,
        request: AddPointCommentRequest,
    ): PointCommentResponse? {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return null
        return client
            .post("$baseUrl/api/v1/trips/$tripId/itinerary/$pointId/comments") {
                setBody(
                    request,
                )
            }.body()
    }

    // -- Sync --

    suspend fun getSnapshot(tripId: String): SnapshotResponse {
        val raw = client.get("$baseUrl/api/v1/trips/$tripId/snapshot").bodyAsText()
        println("[Sync] RAW snapshot body for $tripId: $raw")
        return json.decodeFromString(raw)
    }

    suspend fun getDelta(
        tripId: String,
        cursor: String,
    ): DeltaResponse {
        val raw = client.get("$baseUrl/api/v1/trips/$tripId/sync?cursor=$cursor").bodyAsText()
        println("[Sync] RAW delta body for $tripId (cursor=$cursor): $raw")
        return json.decodeFromString(raw)
    }

    // -- Devices --

    suspend fun registerDevice(request: RegisterDeviceRequest): DeviceResponse =
        client.post("$baseUrl/api/v1/me/devices") { setBody(request) }.body()

    suspend fun removeDevice(deviceId: String) {
        client.delete("$baseUrl/api/v1/me/devices/$deviceId")
    }

    // -- Composite: presign -> S3 PUT -> createAttachment --

    suspend fun uploadFile(
        tripId: String,
        fileBytes: ByteArray,
        fileName: String,
        contentType: String,
    ): AttachmentResponse {
        val presigned =
            presignUpload(
                PresignUploadRequest(
                    fileName,
                    contentType,
                    fileBytes.size.toLong(),
                    tripId,
                ),
            )

        // Upload
        val s3Client = HttpClient()
        try {
            s3Client.put(presigned.uploadUrl) {
                header("Content-Type", contentType)
                setBody(fileBytes)
            }
        } finally {
            s3Client.close()
        }

        return createAttachment(
            tripId,
            CreateAttachmentRequest(
                fileName = fileName,
                fileSize = fileBytes.size.toLong(),
                mimeType = contentType,
                s3Key = presigned.s3Key,
            ),
        )
    }

    suspend fun uploadExpenseFile(
        tripId: String,
        expenseId: String,
        fileBytes: ByteArray,
        fileName: String,
        contentType: String,
    ): AttachmentResponse {
        val presigned =
            presignUpload(
                PresignUploadRequest(
                    fileName,
                    contentType,
                    fileBytes.size.toLong(),
                    tripId,
                ),
            )

        val s3Client = HttpClient()
        try {
            s3Client.put(presigned.uploadUrl) {
                header("Content-Type", contentType)
                setBody(fileBytes)
            }
        } finally {
            s3Client.close()
        }

        return createExpenseAttachment(
            tripId,
            expenseId,
            CreateAttachmentRequest(
                fileName = fileName,
                fileSize = fileBytes.size.toLong(),
                mimeType = contentType,
                s3Key = presigned.s3Key,
            ),
        )
    }

    // -- Feature-flagged (no-op when disabled) --

    suspend fun getChecklist(tripId: String): List<ChecklistItemResponse> {
        if (!BackendFeatureFlags.CHECKLIST_ENABLED) return emptyList()
        return client.get("$baseUrl/api/v1/trips/$tripId/checklist").body()
    }

    suspend fun addChecklistItem(
        tripId: String,
        request: V2CreateChecklistItemRequest,
    ): ChecklistItemResponse? {
        if (!BackendFeatureFlags.CHECKLIST_ENABLED) return null
        return client.post("$baseUrl/api/v1/trips/$tripId/checklist") { setBody(request) }.body()
    }

    suspend fun toggleChecklistItem(
        tripId: String,
        itemId: String,
    ): ChecklistItemResponse? {
        if (!BackendFeatureFlags.CHECKLIST_ENABLED) return null
        return client.put("$baseUrl/api/v1/trips/$tripId/checklist/$itemId/toggle").body()
    }

    suspend fun deleteChecklistItem(
        tripId: String,
        itemId: String,
    ) {
        if (!BackendFeatureFlags.CHECKLIST_ENABLED) return
        client.delete("$baseUrl/api/v1/trips/$tripId/checklist/$itemId")
    }

    suspend fun getTripHistory(tripId: String): List<org.travelplanner.app.features.tripDetails.history.data.HistoryLogDto> {
        if (!BackendFeatureFlags.HISTORY_ENABLED) return emptyList()
        return client.get("$baseUrl/api/v1/trips/$tripId/history").body()
    }

    suspend fun requestJoinTrip(
        code: String,
        userId: String,
        name: String,
    ): TripResponse? {
        if (!BackendFeatureFlags.JOIN_BY_CODE_ENABLED) return null
        return client
            .post("$baseUrl/api/v1/trips/join-request") {
                setBody(V2JoinByCodeRequest(code))
            }.body()
    }

    suspend fun getPendingRequests(tripId: String): List<JoinRequestUserResponse> {
        if (!BackendFeatureFlags.JOIN_BY_CODE_ENABLED) return emptyList()
        return client.get("$baseUrl/api/v1/trips/$tripId/requests").body()
    }

    suspend fun resolveRequest(
        tripId: String,
        userId: String,
        approve: Boolean,
    ) {
        if (!BackendFeatureFlags.JOIN_BY_CODE_ENABLED) return
        client.post("$baseUrl/api/v1/trips/$tripId/requests/$userId/resolve?approve=$approve")
    }

    suspend fun regenerateCode(tripId: String): String? {
        if (!BackendFeatureFlags.JOIN_BY_CODE_ENABLED) return null
        return client
            .post("$baseUrl/api/v1/trips/$tripId/regenerate-code")
            .body<RegenerateCodeResponse>()
            .newCode
    }

    suspend fun resolveConflict(
        tripId: String,
        expenseRemoteId: String,
        accept: Boolean,
    ): ExpenseResponse? {
        if (!BackendFeatureFlags.EXPENSE_CONFLICT_ENABLED) return null
        return client
            .post("$baseUrl/api/v1/trips/$tripId/expenses/$expenseRemoteId/resolve?accept=$accept")
            .body()
    }

    suspend fun resolveConflictMerge(
        tripId: String,
        expenseRemoteId: String,
        merged: V2MergeExpenseRequest,
    ): ExpenseResponse? {
        if (!BackendFeatureFlags.EXPENSE_CONFLICT_ENABLED) return null
        return client
            .post("$baseUrl/api/v1/trips/$tripId/expenses/$expenseRemoteId/resolve-merge") {
                contentType(ContentType.Application.Json)
                setBody(merged)
            }.body()
    }

    suspend fun resolveConflictRevert(
        tripId: String,
        expenseRemoteId: String,
    ): ExpenseResponse? {
        if (!BackendFeatureFlags.EXPENSE_CONFLICT_ENABLED) return null
        return client
            .post("$baseUrl/api/v1/trips/$tripId/expenses/$expenseRemoteId/resolve-revert")
            .body()
    }

    // -- Health --

    suspend fun healthCheck(): Boolean =
        try {
            val response = plainClient.get("$baseUrl/health/live")
            response.status.isSuccess()
        } catch (_: Exception) {
            false
        }
}
