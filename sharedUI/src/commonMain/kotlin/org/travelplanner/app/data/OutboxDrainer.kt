package org.travelplanner.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.travelplanner.app.OutboxEntry
import org.travelplanner.app.core.AddPointCommentRequest
import org.travelplanner.app.core.BackendApiException
import org.travelplanner.app.core.ChangeRoleRequest
import org.travelplanner.app.core.CreateAttachmentRequest
import org.travelplanner.app.core.PendingUpdateStoredException
import org.travelplanner.app.core.PresignUploadRequest
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.V2CreateChecklistItemRequest
import org.travelplanner.app.core.V2CreateExpenseRequest
import org.travelplanner.app.core.V2CreateItineraryPointRequest
import org.travelplanner.app.core.V2CreateTripRequest
import org.travelplanner.app.core.V2UpdateExpenseRequest
import org.travelplanner.app.core.V2UpdateItineraryPointRequest
import org.travelplanner.app.core.V2UpdateTripRequest
import org.travelplanner.app.core.VersionConflictException
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.features.tripDetails.more.checklist.data.ChecklistRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Clock

class OutboxDrainer(
    private val outbox: OutboxRepository,
    private val api: TripApiService,
    private val tripRepo: TripRepository,
    private val expenseRepo: ExpenseRepository,
    private val eventRepo: EventRepository,
    private val checklistRepo: ChecklistRepository,
    private val attachmentStorage: OutboxAttachmentStorage,
    private val db: MyDatabase,
    private val syncTrigger: SyncTrigger,
    private val deltaCoordinator: DeltaSyncCoordinator,
    private val networkState: StateFlow<NetworkState>,
    private val json: Json,
) {
    private val maxBackoffMillis = 30L * 60 * 1000L
    private val baseBackoffMillis = 60L * 1000L
    private val maxAttempts = 1000
    private val maxRebaseAttempts = 3
    private val rebaseWaitTimeoutMillis = 30_000L

    private val rebaseScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun drainAllEligible() {
        if (networkState.value != NetworkState.ONLINE) return
        val now = Clock.System.now().toEpochMilliseconds()
        val trips = outbox.eligibleTripIds(now)
        for (tripId in trips) {
            if (networkState.value != NetworkState.ONLINE) break
            drainTrip(tripId)
        }
    }

    private suspend fun drainTrip(tripId: String) {
        while (networkState.value == NetworkState.ONLINE) {
            val now = Clock.System.now().toEpochMilliseconds()
            val entry = outbox.nextEligible(tripId, now) ?: return
            outbox.markInFlight(entry.id)
            try {
                dispatch(entry)
                outbox.markSuccess(entry.id)
            } catch (e: CancellationException) {
                outbox.markCancelled(entry.id, "cancelled")
                throw e
            } catch (e: VersionConflictException) {
                when (entry.entityType) {
                    OutboxEntityType.EXPENSE,
                    OutboxEntityType.POINT_COMMENT,
                    OutboxEntityType.ATTACHMENT,
                    -> {
                        outbox.markConflict(entry.id, "VERSION_CONFLICT: ${e.error.message}")
                    }

                    else -> {
                        handleVersionConflict(entry, "VERSION_CONFLICT: ${e.error.message}")
                    }
                }
                return
            } catch (e: PendingUpdateStoredException) {
                outbox.markSuccess(entry.id)
                syncTrigger.requestSync()
                return
            } catch (e: BackendApiException) {
                if (e.statusCode == 409) {
                    outbox.markConflict(entry.id, "${e.error.code}: ${e.error.message}")
                    return
                }
                if (e.statusCode in 400..422) {
                    outbox.markDead(
                        entry.id,
                        "HTTP ${e.statusCode} ${e.error.code}: ${e.error.message}",
                    )
                    return
                }
                outbox.markFailed(
                    entry.id,
                    "HTTP ${e.statusCode}: ${e.error.message}",
                    computeBackoff(entry),
                )
                return
            } catch (e: Throwable) {
                outbox.markFailed(
                    entry.id,
                    e.message ?: e::class.simpleName.orEmpty(),
                    computeBackoff(entry),
                )
                return
            }
        }
    }

    private fun computeBackoff(entry: OutboxEntry): Long {
        val attemptCount = entry.attemptCount.toInt().coerceAtMost(maxAttempts)
        val raw = (baseBackoffMillis.toDouble() * 2.0.pow(attemptCount.toDouble())).toLong()
        val capped = min(raw, maxBackoffMillis)
        val jitterRange = (capped / 10).coerceAtLeast(1L)
        val jitter = Random.nextLong(-jitterRange, jitterRange + 1)
        return (capped + jitter).coerceAtLeast(0L)
    }

    private fun handleVersionConflict(
        entry: OutboxEntry,
        errorPrefix: String,
    ) {
        if (entry.rebaseAttempts >= maxRebaseAttempts.toLong()) {
            outbox.markConflict(
                entry.id,
                "$errorPrefix (rebase exhausted after ${entry.rebaseAttempts} attempts)",
            )
            return
        }

        rebaseScope.launch {
            val waiter = deltaCoordinator.awaitNextDeltaForTrip(entry.tripId)
            syncTrigger.requestSync()
            val completed =
                withTimeoutOrNull(rebaseWaitTimeoutMillis) {
                    runCatching { waiter.await() }.isSuccess
                } ?: false
            if (!completed) {
                outbox.markConflict(entry.id, "$errorPrefix (rebase wait timed out)")
                return@launch
            }

            val current = outbox.getEntry(entry.id) ?: return@launch
            if (current.state != OutboxState.IN_FLIGHT) return@launch

            val rebased =
                runCatching { rebasePayloadAfterDelta(current) }.getOrElse { e ->
                    outbox.markConflict(current.id, "$errorPrefix (rebase failed: ${e.message})")
                    return@launch
                }
            if (rebased == null) {
                outbox.markDead(current.id, "$errorPrefix (entity no longer exists locally)")
                return@launch
            }

            outbox.markPendingForRebase(current.id, rebased.payloadJson, rebased.baseVersion)
            syncTrigger.requestSync()
        }
    }

    private data class RebasedPayload(
        val payloadJson: String,
        val baseVersion: Long?,
    )

    private fun rebasePayloadAfterDelta(entry: OutboxEntry): RebasedPayload? {
        val newVersion: Long? =
            when (entry.entityType) {
                OutboxEntityType.TRIP -> {
                    val row =
                        db.tripsQueries.getTripById(entry.entityId).executeAsOneOrNull() ?: return null
                    row.version
                }

                OutboxEntityType.EVENT -> {
                    val row =
                        db.eventsQueries.getEventById(entry.entityId).executeAsOneOrNull()
                            ?: return null
                    row.version
                }

                OutboxEntityType.CHECKLIST -> {
                    val row =
                        db.checklistsQueries
                            .getChecklistForTrip(entry.tripId)
                            .executeAsList()
                            .firstOrNull { it.id == entry.entityId } ?: return null
                    row.version
                }

                OutboxEntityType.PARTICIPANT_ROLE -> {
                    val sep = entry.entityId.indexOf(':')
                    if (sep <= 0) return null
                    val userId = entry.entityId.substring(sep + 1)
                    val row =
                        db.participantsQueries
                            .getParticipantByUserId(entry.tripId, userId)
                            .executeAsOneOrNull()
                            ?: return null
                    row.version
                }

                else -> {
                    return null
                }
            }

        val rebasedJson = rebaseExpectedVersionInPayload(entry.payloadJson, newVersion)
        return RebasedPayload(payloadJson = rebasedJson, baseVersion = newVersion)
    }

    private fun rebaseExpectedVersionInPayload(
        payloadJson: String,
        newVersion: Long?,
    ): String {
        val obj =
            runCatching { json.parseToJsonElement(payloadJson).jsonObject }.getOrNull()
                ?: return payloadJson
        val mutated = LinkedHashMap<String, JsonElement>(obj)
        mutated["expectedVersion"] = if (newVersion != null) JsonPrimitive(newVersion) else JsonNull
        return json.encodeToString(JsonObject.serializer(), JsonObject(mutated))
    }

    private suspend fun dispatch(entry: OutboxEntry) {
        val idempotencyKey = entry.id
        when (entry.entityType) {
            OutboxEntityType.TRIP -> dispatchTrip(entry, idempotencyKey)

            OutboxEntityType.EXPENSE -> dispatchExpense(entry, idempotencyKey)

            OutboxEntityType.EVENT -> dispatchEvent(entry, idempotencyKey)

            OutboxEntityType.CHECKLIST -> dispatchChecklist(entry, idempotencyKey)

            OutboxEntityType.PARTICIPANT_ROLE -> dispatchParticipantRole(entry, idempotencyKey)

            OutboxEntityType.POINT_COMMENT -> dispatchPointComment(entry, idempotencyKey)

            OutboxEntityType.ATTACHMENT -> dispatchAttachment(entry, idempotencyKey)

            else -> throw BackendApiException(
                statusCode = 400,
                error =
                    org.travelplanner.app.core.BackendErrorResponse(
                        code = "UNKNOWN_ENTITY_TYPE",
                        message = "Unknown outbox entity type: ${entry.entityType}",
                    ),
            )
        }
    }

    private suspend fun dispatchTrip(
        entry: OutboxEntry,
        idempotencyKey: String,
    ) {
        when (entry.operation) {
            OutboxOperation.CREATE -> {
                val req = json.decodeFromString(V2CreateTripRequest.serializer(), entry.payloadJson)
                val response = api.createTrip(req, idempotencyKey = idempotencyKey)
                tripRepo.applyServerTripDelta(response)
            }

            OutboxOperation.UPDATE -> {
                val req = json.decodeFromString(V2UpdateTripRequest.serializer(), entry.payloadJson)
                val response = api.updateTrip(entry.entityId, req, idempotencyKey = idempotencyKey)
                tripRepo.applyServerTripDelta(response)
            }

            OutboxOperation.DELETE -> {
                api.deleteTrip(entry.entityId, idempotencyKey = idempotencyKey)
            }
        }
    }

    private suspend fun dispatchExpense(
        entry: OutboxEntry,
        idempotencyKey: String,
    ) {
        when (entry.operation) {
            OutboxOperation.CREATE -> {
                val req =
                    json.decodeFromString(V2CreateExpenseRequest.serializer(), entry.payloadJson)
                val response = api.createExpense(entry.tripId, req, idempotencyKey = idempotencyKey)
                expenseRepo.upsertExpenseFromResponse(entry.tripId, response)
            }

            OutboxOperation.UPDATE -> {
                val req =
                    json.decodeFromString(V2UpdateExpenseRequest.serializer(), entry.payloadJson)
                val response =
                    api.updateExpense(
                        entry.tripId,
                        entry.entityId,
                        req,
                        idempotencyKey = idempotencyKey,
                    )
                expenseRepo.upsertExpenseFromResponse(entry.tripId, response)
            }

            OutboxOperation.DELETE -> {
                api.deleteExpense(entry.tripId, entry.entityId, idempotencyKey = idempotencyKey)
            }
        }
    }

    private suspend fun dispatchEvent(
        entry: OutboxEntry,
        idempotencyKey: String,
    ) {
        when (entry.operation) {
            OutboxOperation.CREATE -> {
                val req =
                    json.decodeFromString(
                        V2CreateItineraryPointRequest.serializer(),
                        entry.payloadJson,
                    )
                val response =
                    api.createItineraryPoint(entry.tripId, req, idempotencyKey = idempotencyKey)
                eventRepo.saveEventFromResponse(entry.tripId, response)
            }

            OutboxOperation.UPDATE -> {
                val req =
                    json.decodeFromString(
                        V2UpdateItineraryPointRequest.serializer(),
                        entry.payloadJson,
                    )
                val response =
                    api.updateItineraryPoint(
                        entry.tripId,
                        entry.entityId,
                        req,
                        idempotencyKey = idempotencyKey,
                    )
                eventRepo.saveEventFromResponse(entry.tripId, response)
            }

            OutboxOperation.DELETE -> {
                api.deleteItineraryPoint(
                    entry.tripId,
                    entry.entityId,
                    idempotencyKey = idempotencyKey,
                )
            }
        }
    }

    private suspend fun dispatchChecklist(
        entry: OutboxEntry,
        idempotencyKey: String,
    ) {
        when (entry.operation) {
            OutboxOperation.CREATE -> {
                val req =
                    json.decodeFromString(
                        V2CreateChecklistItemRequest.serializer(),
                        entry.payloadJson,
                    )
                val response =
                    api.addChecklistItem(entry.tripId, req, idempotencyKey = idempotencyKey)
                if (response != null) checklistRepo.saveLocally(response)
            }

            OutboxOperation.TOGGLE -> {
                val response =
                    api.toggleChecklistItem(
                        entry.tripId,
                        entry.entityId,
                        idempotencyKey = idempotencyKey,
                    )
                if (response != null) checklistRepo.saveLocally(response)
            }

            OutboxOperation.DELETE -> {
                api.deleteChecklistItem(
                    entry.tripId,
                    entry.entityId,
                    idempotencyKey = idempotencyKey,
                )
            }
        }
    }

    private suspend fun dispatchParticipantRole(
        entry: OutboxEntry,
        idempotencyKey: String,
    ) {
        val sep = entry.entityId.indexOf(':')
        if (sep <= 0) {
            throw BackendApiException(
                statusCode = 400,
                error =
                    org.travelplanner.app.core.BackendErrorResponse(
                        code = "MALFORMED_ENTITY_ID",
                        message = "Expected '<tripId>:<userId>' got '${entry.entityId}'",
                    ),
            )
        }
        val userId = entry.entityId.substring(sep + 1)
        val req = json.decodeFromString(ChangeRoleRequest.serializer(), entry.payloadJson)
        api.changeRole(entry.tripId, userId, req, idempotencyKey = idempotencyKey)
    }

    private suspend fun dispatchAttachment(
        entry: OutboxEntry,
        idempotencyKey: String,
    ) {
        if (entry.operation != OutboxOperation.CREATE) {
            throw BackendApiException(
                statusCode = 400,
                error =
                    org.travelplanner.app.core.BackendErrorResponse(
                        code = "UNSUPPORTED_ATTACHMENT_OPERATION",
                        message = "ATTACHMENT outbox entries support CREATE only (got ${entry.operation})",
                    ),
            )
        }

        val payload = json.decodeFromString(AttachmentOutboxPayload.serializer(), entry.payloadJson)

        val (s3Key, uploadUrl) =
            if (payload.presignedS3Key != null && payload.presignedUploadUrl != null) {
                payload.presignedS3Key to payload.presignedUploadUrl
            } else {
                val presigned =
                    api.presignUploadWithKey(
                        PresignUploadRequest(
                            fileName = payload.fileName,
                            contentType = payload.mimeType,
                            fileSize = payload.fileSizeBytes,
                            tripId = payload.tripId,
                            attachmentId = payload.attachmentId,
                        ),
                        idempotencyKey = idempotencyKey,
                    )
                val updated =
                    payload.copy(
                        presignedS3Key = presigned.s3Key,
                        presignedUploadUrl = presigned.uploadUrl,
                    )
                outbox.updatePayloadOnly(
                    id = entry.id,
                    payloadJson = json.encodeToString(AttachmentOutboxPayload.serializer(), updated),
                )
                presigned.s3Key to presigned.uploadUrl
            }

        val bytes =
            attachmentStorage.read(payload.localFilePath)
                ?: throw BackendApiException(
                    statusCode = 410,
                    error =
                        org.travelplanner.app.core.BackendErrorResponse(
                            code = "ATTACHMENT_LOCAL_FILE_MISSING",
                            message = "Local file for attachment ${payload.attachmentId} no longer exists",
                        ),
                )

        try {
            api.s3Put(
                uploadUrl = uploadUrl,
                contentType = payload.mimeType,
                bytes = bytes,
            )
        } catch (e: Throwable) {
            val rolledBack = payload.copy(presignedS3Key = null, presignedUploadUrl = null)
            outbox.updatePayloadOnly(
                id = entry.id,
                payloadJson = json.encodeToString(AttachmentOutboxPayload.serializer(), rolledBack),
            )
            throw e
        }

        val finalizeRequest =
            CreateAttachmentRequest(
                id = payload.attachmentId,
                clientMutationId = idempotencyKey,
                fileName = payload.fileName,
                fileSize = payload.fileSizeBytes,
                mimeType = payload.mimeType,
                s3Key = s3Key,
            )
        val response: org.travelplanner.app.core.AttachmentResponse =
            when {
                payload.expenseId != null -> {
                    api.createExpenseAttachmentWithKey(
                        tripId = payload.tripId,
                        expenseId = payload.expenseId,
                        request = finalizeRequest,
                        idempotencyKey = idempotencyKey,
                    )
                }

                payload.pointId != null -> {
                    api.createPointAttachmentWithKey(
                        tripId = payload.tripId,
                        pointId = payload.pointId,
                        request = finalizeRequest,
                        idempotencyKey = idempotencyKey,
                    )
                }

                else -> {
                    api.createTripAttachmentWithKey(
                        tripId = payload.tripId,
                        request = finalizeRequest,
                        idempotencyKey = idempotencyKey,
                    )
                }
            }

        db.transaction {
            db.attachmentsQueries.insertOrReplaceAttachment(
                response.id,
                response.tripId,
                response.expenseId,
                response.pointId,
                response.uploadedBy,
                response.fileName,
                response.fileSize,
                response.mimeType,
                response.s3Key,
                response.createdAt,
            )
            response.pointId?.let { eventRepo.reconcileFilesJsonFromAttachmentsInTransaction(it) }
            response.expenseId?.let { expenseId ->
                val current = db.expensesQueries.getExpenseById(expenseId).executeAsOneOrNull()
                if (current != null && current.imageUrl?.startsWith("pending://") == true) {
                    db.expensesQueries.updateExpenseDetails(
                        title = current.title,
                        amount = current.amount,
                        category = current.category,
                        payerName = current.payerName,
                        date = current.date,
                        splitDescription = current.splitDescription,
                        currency = current.currency,
                        creatorUserId = current.creatorUserId,
                        pendingUpdateJson = current.pendingUpdateJson,
                        imageUrl = response.s3Key,
                        version = current.version,
                        splitType = current.splitType,
                        id = expenseId,
                    )
                }
            }
        }
        attachmentStorage.delete(payload.localFilePath)
    }

    private suspend fun dispatchPointComment(
        entry: OutboxEntry,
        idempotencyKey: String,
    ) {
        val obj = json.parseToJsonElement(entry.payloadJson).jsonObject
        val pointId =
            obj["__pointId"]?.jsonPrimitive?.contentOrNull
                ?: throw BackendApiException(
                    statusCode = 400,
                    error =
                        org.travelplanner.app.core.BackendErrorResponse(
                            code = "MISSING_POINT_ID",
                            message = "POINT_COMMENT outbox entry missing __pointId in payload",
                        ),
                )
        val cleanPayload = JsonObject(obj.filterKeys { it != "__pointId" })
        val req =
            json.decodeFromString(
                AddPointCommentRequest.serializer(),
                json.encodeToString(JsonElement.serializer(), cleanPayload),
            )
        api.addPointComment(entry.tripId, pointId, req, idempotencyKey = idempotencyKey)
    }

    fun reclaim() {
        outbox.reclaimInFlight()
    }

    fun cancelRebases() {
        rebaseScope.coroutineContext.cancelChildren()
    }
}
