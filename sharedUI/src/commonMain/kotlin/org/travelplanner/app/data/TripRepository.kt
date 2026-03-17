package org.travelplanner.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.travelplanner.app.TripEntity
import org.travelplanner.app.core.CreateTripRequest
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.TripDto
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.Trip
import org.travelplanner.app.domain.toDomain

class TripRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
) {
    private val queries = db.tripsQueries

    fun resolveUrl(path: String?): String? = api.resolveUrl(path)

    private fun getTripsEntityFlow(): Flow<List<TripEntity>> =
        queries.getAllTrips().asFlow().mapToList(
            Dispatchers.IO,
        )

    private fun getTripByIdEntity(id: Long): Flow<TripEntity?> = queries.getTripById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    fun getTripsFlow(): Flow<List<Trip>> = getTripsEntityFlow().map { list -> list.map { it.toDomain() } }

    fun getTripById(id: Long): Flow<Trip?> = getTripByIdEntity(id).map { it?.toDomain() }

    fun insertPendingTrip(dto: TripDto) {
        queries.insertTripWithId(
            id = dto.id,
            title = dto.title,
            destination = dto.destination,
            startDate = dto.startDate,
            endDate = dto.endDate,
            currency = dto.currency,
            totalBudget = dto.totalBudget,
            description = dto.description,
            participantCount = 1,
            status = "PENDING_JOIN",
            joinCode = dto.joinCode,
            ownerUserId = dto.ownerUserId,
            imageUrl = dto.imageUrl,
            filesJson = null,
        )
    }

    fun saveServerTrip(trip: Trip) {
        queries.transaction {
            queries.insertTripWithId(
                id = trip.id,
                title = trip.title,
                destination = trip.destination,
                startDate = trip.startDate,
                endDate = trip.endDate,
                currency = trip.currency,
                totalBudget = trip.totalBudget,
                description = trip.description,
                participantCount = trip.participantCount.toLong(),
                status = trip.status,
                joinCode = trip.joinCode,
                ownerUserId = trip.ownerUserId,
                imageUrl = trip.imageUrl,
                filesJson = null,
            )
        }
    }

    fun saveSyncedTrips(remoteTrips: List<TripDto>) {
        queries.transaction {
            val localTripIds = queries.getAllTrips().executeAsList().map { it.id }

            val remoteTripIds = remoteTrips.map { it.id }.toSet()

            remoteTrips.forEach { dto ->
                val existing = queries.getTripById(dto.id).executeAsOneOrNull()

                val newStatus =
                    when (existing?.status) {
                        "ARCHIVED" -> "ARCHIVED"
                        "PENDING_JOIN" -> "PLANNED"
                        else -> existing?.status ?: "PLANNED"
                    }

                queries.insertTripWithId(
                    id = dto.id,
                    title = dto.title,
                    destination = dto.destination,
                    startDate = dto.startDate,
                    endDate = dto.endDate,
                    currency = dto.currency,
                    totalBudget = dto.totalBudget,
                    description = dto.description,
                    participantCount = existing?.participantCount ?: 1L,
                    status = newStatus,
                    joinCode = dto.joinCode,
                    ownerUserId = dto.ownerUserId,
                    imageUrl = dto.imageUrl,
                    filesJson = dto.filesJson,
                )

                queries.updateTripSpentAmount(dto.id)
                queries.updateTripParticipantCount(dto.id)
            }

            val tripsToDelete = localTripIds.filter { it !in remoteTripIds }
            tripsToDelete.forEach { obsoleteTripId ->
                queries.deleteTripLocal(obsoleteTripId)
            }
        }
    }

    fun updateTripStatusLocal(
        tripId: Long,
        status: String,
    ) {
        queries.updateTripStatusLocal(status, tripId)
    }

    suspend fun changeTripBudget(
        tripId: Long,
        newBudget: Double,
    ) {
        api.updateTripBudget(tripId, newBudget)
        updateTripBudgetLocal(tripId, newBudget)
    }

    fun updateTripBudgetLocal(
        tripId: Long,
        newBudget: Double,
    ) {
        db.transaction {
            queries.updateTripBudgetLocal(newBudget, tripId)
        }
    }

    fun updateJoinCodeLocal(
        tripId: Long,
        newCode: String,
    ) {
        queries.updateJoinCode(newCode, tripId)
    }

    fun updateTripFilesJson(
        tripId: Long,
        filesJson: String,
    ) {
        queries.updateTripFilesJson(filesJson, tripId)
    }

    fun deleteTripLocal(tripId: Long) = queries.deleteTripLocal(tripId)

    suspend fun syncTripsFromServer(): List<TripDto> = api.getUserTrips()

    suspend fun requestJoinTrip(
        code: String,
        userId: String,
        name: String,
    ): TripDto = api.requestJoinTrip(code, userId, name)

    suspend fun createTrip(request: CreateTripRequest): TripDto = api.createTrip(request)

    suspend fun uploadPhoto(bytes: ByteArray): String = api.uploadPhoto(bytes)

    suspend fun uploadFile(
        bytes: ByteArray,
        fileName: String,
    ): String = api.uploadFile(bytes, fileName)

    suspend fun deleteOrLeaveTrip(tripId: Long) {
        api.deleteOrLeaveTrip(tripId)
        deleteTripLocal(tripId)
    }

    suspend fun setTripStatus(
        tripId: Long,
        status: String,
    ) {
        api.setTripStatus(tripId, status)
        updateTripStatusLocal(tripId, status)
    }

    suspend fun regenerateCode(tripId: Long): String {
        val newCode = api.regenerateCode(tripId)
        updateJoinCodeLocal(tripId, newCode)
        return newCode
    }

    suspend fun updateTripFilesRemote(
        tripId: Long,
        filesJson: String,
    ) {
        updateTripFilesJson(tripId, filesJson)
        api.updateTripFiles(tripId, filesJson)
    }
}
