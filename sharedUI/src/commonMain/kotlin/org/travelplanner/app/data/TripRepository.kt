package org.travelplanner.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.travelplanner.app.AttachmentEntity
import org.travelplanner.app.TripEntity
import org.travelplanner.app.core.AttachmentResponse
import org.travelplanner.app.core.BackendFeatureFlags
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.TripResponse
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.V2CreateTripRequest
import org.travelplanner.app.core.V2UpdateTripRequest
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.Trip
import org.travelplanner.app.domain.toDomain

class TripRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
    private val outbox: OutboxRepository,
    private val attachmentStorage: OutboxAttachmentStorage,
    private val userSession: UserSession,
    private val syncTrigger: SyncTrigger,
    private val json: Json,
) {
    private val queries = db.tripsQueries

    private fun getTripsEntityFlow(): Flow<List<TripEntity>> = queries.getAllTrips().asFlow().mapToList(Dispatchers.IO)

    private fun getTripByIdEntity(id: String): Flow<TripEntity?> = queries.getTripById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    fun getTripsFlow(): Flow<List<Trip>> = getTripsEntityFlow().map { list -> list.map { it.toDomain() } }

    fun getTripById(id: String): Flow<Trip?> = getTripByIdEntity(id).map { it?.toDomain() }

    fun saveServerTrip(trip: Trip) {
        queries.transaction {
            val existingSpent = queries.getTripById(trip.id).executeAsOneOrNull()?.spentAmount
            queries.insertTripWithId(
                id = trip.id,
                title = trip.title,
                destination = trip.destination,
                startDate = trip.startDate,
                endDate = trip.endDate,
                currency = trip.currency,
                totalBudget = trip.totalBudget,
                spentAmount = existingSpent ?: trip.spentAmount,
                description = trip.description,
                participantCount = trip.participantCount.toLong(),
                status = trip.status,
                joinCode = trip.joinCode,
                ownerUserId = trip.ownerUserId,
                imageUrl = trip.imageUrl,
                filesJson = trip.filesJson,
                baseCurrency = trip.baseCurrency,
                version = trip.version,
                createdBy = trip.createdBy,
                createdAt = trip.createdAt,
                updatedAt = trip.updatedAt,
            )
        }
    }

    fun saveSyncedTrips(remoteTrips: List<TripResponse>) {
        queries.transaction {
            val localTripIds = queries.getAllTrips().executeAsList().map { it.id }
            val remoteTripIds = remoteTrips.map { it.id }.toSet()

            remoteTrips.forEach { mergeTripFromServer(it) }

            val tripsToDelete = localTripIds.filter { it !in remoteTripIds }
            tripsToDelete.forEach { obsoleteTripId ->
                db.expensesQueries.deleteExpensesForTrip(obsoleteTripId)
                db.eventsQueries.deleteEventsForTrip(obsoleteTripId)
                db.participantsQueries.deleteParticipantsForTrip(obsoleteTripId)
                db.historyQueries.deleteLogsForTrip(obsoleteTripId)
                db.checklistsQueries.deleteChecklistForTrip(obsoleteTripId)
                db.attachmentsQueries.deleteAttachmentsForTrip(obsoleteTripId)
                db.syncCursorsQueries.deleteCursor(obsoleteTripId)
                queries.deleteTripLocal(obsoleteTripId)
            }
        }
    }

    fun applyServerTripDelta(response: TripResponse) {
        queries.transaction { mergeTripFromServer(response) }
    }

    private fun mergeTripFromServer(response: TripResponse) {
        val existing = queries.getTripById(response.id).executeAsOneOrNull()

        val newStatus =
            when (existing?.status) {
                "ARCHIVED" -> "ARCHIVED"
                "PENDING_JOIN" -> "PLANNED"
                else -> existing?.status ?: response.status
            }

        queries.insertTripWithId(
            id = response.id,
            title = response.title,
            destination = response.destination.ifBlank { existing?.destination ?: "" },
            startDate = response.startDate,
            endDate = response.endDate,
            currency = response.baseCurrency,
            totalBudget = response.totalBudget.ifBlank { existing?.totalBudget ?: "0" },
            spentAmount = existing?.spentAmount ?: "0",
            description = response.description,
            participantCount = existing?.participantCount ?: 1L,
            status = newStatus,
            joinCode = if (response.joinCode.isNotBlank()) response.joinCode else existing?.joinCode,
            ownerUserId = response.createdBy,
            imageUrl = response.imageUrl ?: existing?.imageUrl,
            filesJson = existing?.filesJson,
            baseCurrency = response.baseCurrency,
            version = response.version,
            createdBy = response.createdBy,
            createdAt = response.createdAt,
            updatedAt = response.updatedAt,
        )

        queries.updateTripParticipantCount(response.id)
    }

    fun updateTripStatusLocal(
        tripId: String,
        status: String,
    ) {
        queries.updateTripStatusLocal(status, tripId)
    }

    fun createTripLocal(request: V2CreateTripRequest): String {
        val tripId = outbox.newMutationId()
        val mutationId = outbox.newMutationId()
        val ownerId =
            userSession.currentUser.value
                ?.id
                .orEmpty()
        val now = isoNow()
        val payload =
            request.copy(
                id = tripId,
                clientMutationId = mutationId,
            )

        db.transaction {
            queries.insertTripWithId(
                id = tripId,
                title = payload.title,
                destination = payload.destination ?: "",
                startDate = payload.startDate,
                endDate = payload.endDate,
                currency = payload.baseCurrency,
                totalBudget = payload.totalBudget ?: "0",
                spentAmount = "0",
                description = payload.description,
                participantCount = 1L,
                status = "PLANNED",
                joinCode = null,
                ownerUserId = ownerId,
                imageUrl = payload.imageUrl,
                filesJson = null,
                baseCurrency = payload.baseCurrency,
                version = 0L,
                createdBy = ownerId,
                createdAt = now,
                updatedAt = now,
            )

            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.TRIP,
                entityId = tripId,
                operation = OutboxOperation.CREATE,
                payloadJson = json.encodeToString(V2CreateTripRequest.serializer(), payload),
                baseVersion = null,
                existingMutationId = mutationId,
            )
        }
        syncTrigger.requestSync()
        return tripId
    }

    private fun updateTripLocal(
        tripId: String,
        update: V2UpdateTripRequest,
        applyLocally: () -> Unit,
    ) {
        val existing = queries.getTripById(tripId).executeAsOneOrNull() ?: return
        val mutationId = outbox.newMutationId()
        val payload =
            update.copy(
                expectedVersion = update.expectedVersion ?: existing.version,
                clientMutationId = mutationId,
            )

        db.transaction {
            applyLocally()
            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.TRIP,
                entityId = tripId,
                operation = OutboxOperation.UPDATE,
                payloadJson = json.encodeToString(V2UpdateTripRequest.serializer(), payload),
                baseVersion = existing.version,
                existingMutationId = mutationId,
            )
        }
        syncTrigger.requestSync()
    }

    fun changeTripBudget(
        tripId: String,
        newBudget: String,
    ) {
        val normalised = newBudget.toDoubleOrNull()?.toString() ?: newBudget
        updateTripLocal(
            tripId,
            V2UpdateTripRequest(totalBudget = normalised),
        ) {
            queries.updateTripBudgetLocal(newBudget, tripId)
        }
    }

    fun setTripImageUrl(
        tripId: String,
        imageUrl: String,
    ) {
        updateTripLocal(
            tripId,
            V2UpdateTripRequest(imageUrl = imageUrl),
        ) {
            queries.updateTripImageUrlLocal(imageUrl, tripId)
        }
    }

    fun setTripStatus(
        tripId: String,
        status: String,
    ) {
        updateTripLocal(
            tripId,
            V2UpdateTripRequest(status = status),
        ) {
            updateTripStatusLocal(tripId, status)
        }
    }

    fun updateTripBudgetLocal(
        tripId: String,
        newBudget: String,
    ) {
        db.transaction {
            queries.updateTripBudgetLocal(newBudget, tripId)
        }
    }

    fun updateJoinCodeLocal(
        tripId: String,
        newCode: String,
    ) {
        queries.updateJoinCode(newCode, tripId)
    }

    fun deleteTripLocal(tripId: String) = queries.deleteTripLocal(tripId)

    fun deleteTripCascade(tripId: String) {
        val pendingPaths =
            db.attachmentsQueries
                .getAttachmentIdAndKeyByTrip(tripId)
                .executeAsList()
                .filter { it.s3Key.startsWith("pending://") }
                .map { it.s3Key.removePrefix("pending://") }

        db.transaction {
            db.expensesQueries.deleteExpensesForTrip(tripId)
            db.eventsQueries.deleteEventsForTrip(tripId)
            db.participantsQueries.deleteParticipantsForTrip(tripId)
            db.historyQueries.deleteLogsForTrip(tripId)
            db.checklistsQueries.deleteChecklistForTrip(tripId)
            db.attachmentsQueries.deleteAttachmentsForTrip(tripId)
            db.syncCursorsQueries.deleteCursor(tripId)
            outbox.deleteAllForTrip(tripId)
            queries.deleteTripLocal(tripId)
        }
        pendingPaths.forEach { attachmentStorage.delete(it) }
    }

    suspend fun syncTripsFromServer(): List<TripResponse> = api.getTrips()

    suspend fun uploadPhoto(
        tripId: String,
        bytes: ByteArray,
    ): String {
        val attachment = api.uploadFile(tripId, bytes, "photo.jpg", "image/jpeg")
        return attachment.s3Key
    }

    suspend fun uploadFile(
        tripId: String,
        bytes: ByteArray,
        fileName: String,
    ): AttachmentResponse = api.uploadFile(tripId, bytes, fileName, mimeTypeForFileName(fileName))

    fun enqueueTripFileAttachment(
        tripId: String,
        bytes: ByteArray,
        fileName: String,
    ): String? {
        val attachmentId = outbox.newMutationId()
        val absolutePath =
            runCatching { attachmentStorage.write(attachmentId, bytes) }
                .getOrNull() ?: return null
        val mimeType = mimeTypeForFileName(fileName)
        val now =
            kotlin.time.Instant
                .fromEpochMilliseconds(
                    kotlin.time.Clock.System
                        .now()
                        .toEpochMilliseconds(),
                ).toString()

        db.transaction {
            db.attachmentsQueries.insertOrReplaceAttachment(
                attachmentId,
                tripId,
                null,
                null,
                userSession.currentUser.value
                    ?.id
                    .orEmpty(),
                fileName,
                bytes.size.toLong(),
                mimeType,
                "pending://$absolutePath",
                now,
            )

            val payload =
                AttachmentOutboxPayload(
                    attachmentId = attachmentId,
                    tripId = tripId,
                    expenseId = null,
                    pointId = null,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSizeBytes = bytes.size.toLong(),
                    localFilePath = absolutePath,
                )
            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.ATTACHMENT,
                entityId = attachmentId,
                operation = OutboxOperation.CREATE,
                payloadJson = json.encodeToString(AttachmentOutboxPayload.serializer(), payload),
                baseVersion = null,
            )
        }
        syncTrigger.requestSync()
        return attachmentId
    }

    fun getTripLevelAttachmentsFlow(tripId: String): Flow<List<AttachmentEntity>> =
        db.attachmentsQueries
            .getTripLevelAttachmentsForTrip(tripId)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun saveAttachmentLocally(att: AttachmentResponse) {
        db.attachmentsQueries.insertOrReplaceAttachment(
            att.id,
            att.tripId,
            att.expenseId,
            att.pointId,
            att.uploadedBy,
            att.fileName,
            att.fileSize,
            att.mimeType,
            att.s3Key,
            att.createdAt,
        )
    }

    fun saveFakeAttachmentLocally(
        tripId: String,
        fileName: String,
        fileSize: Long,
    ): AttachmentResponse {
        val now = kotlin.time.Clock.System.now().toString()
        val safeFileName = fileName.ifBlank { "file.bin" }
        val localId = "local-${kotlin.time.Clock.System.now().toEpochMilliseconds()}-${safeFileName.hashCode()}"
        val localKey = "local://$tripId/$localId/$safeFileName"
        val attachment =
            AttachmentResponse(
                id = localId,
                tripId = tripId,
                uploadedBy = "local",
                fileName = safeFileName,
                fileSize = fileSize,
                mimeType = mimeTypeForFileName(safeFileName),
                s3Key = localKey,
                createdAt = now,
            )
        saveAttachmentLocally(attachment)
        return attachment
    }

    suspend fun refreshTripFiles(tripId: String) {
        if (!BackendFeatureFlags.TRIP_FILES_ENABLED) return
        try {
            api.listTripFiles(tripId, scope = "trip").forEach { saveAttachmentLocally(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private val downloadUrlCache = mutableMapOf<String, String>()

    suspend fun getDownloadUrl(s3Key: String): String {
        downloadUrlCache[s3Key]?.let { return it }
        val url = api.presignDownload(s3Key).url
        downloadUrlCache[s3Key] = url
        return url
    }

    fun deleteOrLeaveTrip(tripId: String) {
        val existing = queries.getTripById(tripId).executeAsOneOrNull() ?: return
        val mutationId = outbox.newMutationId()
        val payloadJson =
            json.encodeToString(
                V2UpdateTripRequest.serializer(),
                V2UpdateTripRequest(clientMutationId = mutationId, expectedVersion = existing.version),
            )

        val pendingPaths =
            db.attachmentsQueries
                .getAttachmentIdAndKeyByTrip(tripId)
                .executeAsList()
                .filter { it.s3Key.startsWith("pending://") }
                .map { it.s3Key.removePrefix("pending://") }

        db.transaction {
            outbox.deleteAllForTrip(tripId)
            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.TRIP,
                entityId = tripId,
                operation = OutboxOperation.DELETE,
                payloadJson = payloadJson,
                baseVersion = existing.version,
                existingMutationId = mutationId,
            )
            db.expensesQueries.deleteExpensesForTrip(tripId)
            db.eventsQueries.deleteEventsForTrip(tripId)
            db.participantsQueries.deleteParticipantsForTrip(tripId)
            db.historyQueries.deleteLogsForTrip(tripId)
            db.checklistsQueries.deleteChecklistForTrip(tripId)
            db.attachmentsQueries.deleteAttachmentsForTrip(tripId)
            db.syncCursorsQueries.deleteCursor(tripId)
            queries.deleteTripLocal(tripId)
        }
        pendingPaths.forEach { attachmentStorage.delete(it) }
        syncTrigger.requestSync()
    }

    suspend fun regenerateCode(tripId: String): String {
        if (!BackendFeatureFlags.JOIN_BY_CODE_ENABLED) return ""
        val newCode = api.regenerateCode(tripId) ?: return ""
        updateJoinCodeLocal(tripId, newCode)
        return newCode
    }

    @Suppress("DEPRECATION")
    suspend fun requestJoinTrip(
        code: String,
        userId: String,
        name: String,
    ): TripResponse? {
        if (!BackendFeatureFlags.JOIN_BY_CODE_ENABLED) return null
        return api.requestJoinTrip(code, userId, name)
    }

    private fun isoNow(): String =
        kotlin.time.Instant
            .fromEpochMilliseconds(kotlin.time.Clock.System.now().toEpochMilliseconds())
            .toString()
}
