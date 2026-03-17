package org.travelplanner.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.travelplanner.app.TripParticipantEntity
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.TripUtils
import org.travelplanner.app.core.UserDto
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.domain.PendingUser
import org.travelplanner.app.domain.toDomain
import org.travelplanner.app.domain.toPendingUser

class ParticipantRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
    private val userSession: UserSession,
) {
    private val queries = db.participantsQueries

    private fun getParticipantsEntityFlow(tripId: Long): Flow<List<TripParticipantEntity>> =
        queries.getParticipantsForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    fun getParticipantsFlow(tripId: Long): Flow<List<Participant>> =
        getParticipantsEntityFlow(tripId).map { list -> list.map { it.toDomain() } }

    suspend fun syncParticipants(tripId: Long) {
        val currentUser = userSession.currentUser.value ?: return

        try {
            val remoteUsers = api.getParticipants(tripId)
            val remoteUserIds = remoteUsers.map { it.id }.toSet()

            db.transaction {
                val trip = db.tripsQueries.getTripById(tripId).executeAsOneOrNull()
                val ownerId = trip?.ownerUserId

                val localParticipants = queries.getParticipantsForTrip(tripId).executeAsList()

                localParticipants.forEach { local ->
                    if (local.userId != null && local.role != "LEFT" && local.userId !in remoteUserIds) {
                        if (local.userId != ownerId) {
                            queries.updateParticipantRole("LEFT", tripId, local.userId)
                        }
                    }
                }

                remoteUsers.forEach { userDto ->
                    insertOrUpdateParticipant(tripId, userDto)
                }

                if (ownerId == currentUser.id.toString() && currentUser.id.toString() !in remoteUserIds) {
                    insertOrUpdateParticipant(
                        tripId,
                        UserDto(currentUser.id.toString(), currentUser.name, currentUser.email),
                    )
                }

                queries.updateTripParticipantCount(tripId)
            }
        } catch (e: Exception) {
            println("Participant sync failed (Offline mode): ${e.message}")
        }
    }

    fun insertOrUpdateParticipant(
        tripId: Long,
        userDto: UserDto,
    ) {
        val trip = db.tripsQueries.getTripById(tripId).executeAsOneOrNull()

        val role = if (trip?.ownerUserId == userDto.id) "OWNER" else "VIEWER"

        val existing = queries.getParticipantByUserId(tripId, userDto.id).executeAsOneOrNull()

        if (existing != null) {
            queries.updateParticipantDetails(
                name = userDto.name,
                email = userDto.email,
                role = role,
                tripId = tripId,
                userId = userDto.id,
            )
        } else {
            queries.insertParticipant(
                tripId = tripId,
                userId = userDto.id,
                name = userDto.name,
                email = userDto.email,
                role = role,
                avatarColor1 = TripUtils.generateRandomColor(),
                avatarColor2 = TripUtils.generateRandomColor(),
            )
        }
    }

    fun handleParticipantLeft(
        tripId: Long,
        userId: String,
    ) {
        db.transaction {
            queries.updateParticipantRole("LEFT", tripId, userId)
            queries.updateTripParticipantCount(tripId)
        }
    }

    fun getOrCreateParticipantLocal(
        tripId: Long,
        globalUserId: String,
    ): TripParticipantEntity {
        val byId = queries.getParticipantByUserId(tripId, globalUserId).executeAsOneOrNull()
        if (byId != null) return byId

        val byName =
            queries
                .getParticipantsForTrip(tripId)
                .executeAsList()
                .find { it.name == globalUserId }
        if (byName != null) return byName

        val newName = "User $globalUserId"

        println("Repo: Creating Stub Participant for ID: $globalUserId")

        queries.insertParticipant(
            tripId = tripId,
            userId = globalUserId,
            name = newName,
            email = "stub_$globalUserId@app.com",
            role = "VIEWER",
            avatarColor1 = "#666666",
            avatarColor2 = "#999999",
        )
        val newId = queries.lastInsertRowId().executeAsOne()
        return queries.getParticipantsForTrip(tripId).executeAsList().first { it.id == newId }
    }

    suspend fun getPendingRequests(tripId: Long): List<PendingUser> = api.getPendingRequests(tripId).map { it.toPendingUser() }

    suspend fun resolveRequest(
        tripId: Long,
        userId: String,
        approve: Boolean,
    ) = api.resolveRequest(tripId, userId, approve)
}
