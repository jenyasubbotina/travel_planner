package org.travelplanner.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.travelplanner.app.TripJoinRequestEntity
import org.travelplanner.app.TripParticipantEntity
import org.travelplanner.app.core.BackendFeatureFlags
import org.travelplanner.app.core.InvitationResponse
import org.travelplanner.app.core.InviteParticipantRequest
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.domain.PendingUser
import org.travelplanner.app.domain.toDomain

class ParticipantRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
    private val userSession: UserSession,
) {
    private val queries = db.participantsQueries
    private val joinRequestQueries = db.joinRequestsQueries

    private fun getParticipantsEntityFlow(tripId: String): Flow<List<TripParticipantEntity>> =
        queries.getParticipantsForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    fun getParticipantsFlow(tripId: String): Flow<List<Participant>> =
        getParticipantsEntityFlow(tripId).map { list -> list.map { it.toDomain() } }

    fun getPendingRequestsFlow(tripId: String): Flow<List<PendingUser>> =
        joinRequestQueries.getPendingRequestsForTrip(tripId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toPendingUser() } }

    suspend fun syncParticipants(tripId: String) {
        val currentUser = userSession.currentUser.value ?: return

        try {
            val remoteParticipants = api.getParticipants(tripId)
            val remoteUserIds = remoteParticipants.map { it.userId }.toSet()

            db.transaction {
                val trip = db.tripsQueries.getTripById(tripId).executeAsOneOrNull()
                val ownerId = trip?.ownerUserId

                val localParticipants = queries.getParticipantsForTrip(tripId).executeAsList()

                localParticipants.forEach { local ->
                    if (local.role != "LEFT" && local.userId !in remoteUserIds) {
                        if (local.userId != ownerId) {
                            queries.updateParticipantRole("LEFT", tripId, local.userId)
                        }
                    }
                }

                remoteParticipants.forEach { detail ->
                    val existing = queries.getParticipantByUserId(tripId, detail.userId).executeAsOneOrNull()
                    if (existing != null) {
                        queries.updateParticipantDetails(
                            name = detail.displayName,
                            email = detail.email,
                            role = detail.role,
                            tripId = tripId,
                            userId = detail.userId,
                        )
                    } else {
                        queries.insertParticipant(
                            tripId = tripId,
                            userId = detail.userId,
                            name = detail.displayName,
                            email = detail.email,
                            role = detail.role,
                            joinedAt = detail.joinedAt,
                            avatarUrl = detail.avatarUrl,
                        )
                    }
                }

                queries.updateTripParticipantCount(tripId)
            }
        } catch (e: Exception) {
            println("Participant sync failed (Offline mode): ${e.message}")
        }
    }

    fun handleParticipantLeft(tripId: String, userId: String) {
        db.transaction {
            queries.updateParticipantRole("LEFT", tripId, userId)
            queries.updateTripParticipantCount(tripId)
        }
    }

    fun getOrCreateParticipantLocal(tripId: String, globalUserId: String): TripParticipantEntity {
        val byId = queries.getParticipantByUserId(tripId, globalUserId).executeAsOneOrNull()
        if (byId != null) return byId

        val byName = queries.getParticipantsForTrip(tripId).executeAsList().find { it.name == globalUserId }
        if (byName != null) return byName

        queries.insertParticipant(
            tripId = tripId,
            userId = globalUserId,
            name = "Пользователь $globalUserId",
            email = "stub_$globalUserId@app.com",
            role = "VIEWER",
            joinedAt = null,
            avatarUrl = null,
        )
        val newId = queries.lastInsertRowId().executeAsOne()
        return queries.getParticipantsForTrip(tripId).executeAsList().first { it.id == newId }
    }

    suspend fun acceptInvitation(invitationId: String) {
        api.acceptInvitation(invitationId)
    }

    suspend fun inviteByEmail(
        tripId: String,
        email: String,
        role: String = "EDITOR",
    ): InvitationResponse =
        api.inviteParticipant(
            tripId,
            InviteParticipantRequest(email = email.trim(), role = role),
        )

    suspend fun resolveRequest(tripId: String, userId: String, approve: Boolean) {
        if (!BackendFeatureFlags.JOIN_BY_CODE_ENABLED) return
        api.resolveRequest(tripId, userId, approve)

        db.transaction {
            joinRequestQueries.getPendingRequestsForTrip(tripId).executeAsList()
                .firstOrNull { it.requesterUserId == userId }
                ?.let { joinRequestQueries.deleteJoinRequest(it.id) }
        }
    }

    private fun TripJoinRequestEntity.toPendingUser(): PendingUser =
        PendingUser(
            id = requesterUserId,
            name = displayName,
            email = email,
        )
}
