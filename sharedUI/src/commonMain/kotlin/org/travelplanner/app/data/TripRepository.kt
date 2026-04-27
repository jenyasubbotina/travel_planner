package org.travelplanner.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.travelplanner.app.AttachmentEntity
import org.travelplanner.app.TripEntity
import org.travelplanner.app.core.AttachmentResponse
import org.travelplanner.app.core.BackendFeatureFlags
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.TripResponse
import org.travelplanner.app.core.V2CreateTripRequest
import org.travelplanner.app.core.V2UpdateTripRequest
import org.travelplanner.app.core.VersionConflictException
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.Trip
import org.travelplanner.app.domain.toDomain

class TripRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
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

    suspend fun changeTripBudget(
        tripId: String,
        newBudget: String,
    ) {
        val normalised = newBudget.toDoubleOrNull()?.toString() ?: newBudget
        // Update local first so the UI reflects the change even if the network is flaky.
        updateTripBudgetLocal(tripId, newBudget)
        patchTripWithRetry(tripId) { v ->
            V2UpdateTripRequest(totalBudget = normalised, expectedVersion = v)
        }
    }

    suspend fun setTripImageUrl(
        tripId: String,
        imageUrl: String,
    ) {
        queries.updateTripImageUrlLocal(imageUrl, tripId)
        patchTripWithRetry(tripId) { v ->
            V2UpdateTripRequest(imageUrl = imageUrl, expectedVersion = v)
        }
    }

    private suspend fun patchTripWithRetry(
        tripId: String,
        buildRequest: (expectedVersion: Long) -> V2UpdateTripRequest,
    ): TripResponse? {
        val localVersion = queries.getTripById(tripId).executeAsOneOrNull()?.version ?: 0L
        return try {
            val response = api.updateTrip(tripId, buildRequest(localVersion))
            applyServerTripDelta(response)
            response
        } catch (e: CancellationException) {
            throw e
        } catch (e: VersionConflictException) {
            val freshVersion =
                runCatching { api.getTrip(tripId).version }.getOrNull()
                    ?: return null
            try {
                val response = api.updateTrip(tripId, buildRequest(freshVersion))
                applyServerTripDelta(response)
                response
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Exception) {
                println("[TripRepository] PATCH retry failed for $tripId: ${e2.message}")
                null
            }
        } catch (e: Exception) {
            println("[TripRepository] PATCH failed for $tripId: ${e.message}")
            null
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
        db.transaction {
            db.expensesQueries.deleteExpensesForTrip(tripId)
            db.eventsQueries.deleteEventsForTrip(tripId)
            db.participantsQueries.deleteParticipantsForTrip(tripId)
            db.historyQueries.deleteLogsForTrip(tripId)
            db.checklistsQueries.deleteChecklistForTrip(tripId)
            db.attachmentsQueries.deleteAttachmentsForTrip(tripId)
            db.syncCursorsQueries.deleteCursor(tripId)
            queries.deleteTripLocal(tripId)
        }
    }

    suspend fun syncTripsFromServer(): List<TripResponse> = api.getTrips()

    suspend fun createTrip(request: V2CreateTripRequest): TripResponse = api.createTrip(request)

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

    suspend fun refreshTripFiles(tripId: String) {
        if (!BackendFeatureFlags.TRIP_FILES_ENABLED) return
        try {
            api.listTripFiles(tripId, scope = "trip").forEach { saveAttachmentLocally(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun getDownloadUrl(s3Key: String): String = api.presignDownload(s3Key).url

    suspend fun deleteOrLeaveTrip(tripId: String) {
        api.deleteTrip(tripId)
        deleteTripCascade(tripId)
    }

    suspend fun setTripStatus(
        tripId: String,
        status: String,
    ) {
        updateTripStatusLocal(tripId, status)
        patchTripWithRetry(tripId) { v ->
            V2UpdateTripRequest(status = status, expectedVersion = v)
        }
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
}
