package org.travelplanner.app.features.tripDetails.more.checklist.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import org.travelplanner.app.AppBackground
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.travelplanner.app.TripChecklistEntity
import org.travelplanner.app.core.BackendFeatureFlags
import org.travelplanner.app.core.ChecklistItemResponse
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.V2CreateChecklistItemRequest
import org.travelplanner.app.data.OutboxEntityType
import org.travelplanner.app.data.OutboxOperation
import org.travelplanner.app.data.OutboxRepository
import org.travelplanner.app.data.SyncTrigger
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.ChecklistItem
import org.travelplanner.app.domain.toDomain

class ChecklistRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
    private val outbox: OutboxRepository,
    private val userSession: UserSession,
    private val syncTrigger: SyncTrigger,
    private val json: Json,
) {
    private val queries = db.checklistsQueries

    private fun getChecklistEntityFlow(tripId: String): Flow<List<TripChecklistEntity>> =
        queries.getChecklistForTrip(tripId).asFlow().mapToList(AppBackground)

    fun getChecklistFlow(tripId: String): Flow<List<ChecklistItem>> =
        getChecklistEntityFlow(tripId).map { list -> list.map { it.toDomain(json) } }

    suspend fun syncChecklist(tripId: String) {
        if (!BackendFeatureFlags.CHECKLIST_ENABLED) return
        try {
            val remoteList = api.getChecklist(tripId)
            db.transaction {
                val remoteIds = remoteList.map { it.id }.toSet()
                val localItems = queries.getChecklistForTrip(tripId).executeAsList()
                localItems.forEach { local ->
                    if (local.id !in remoteIds) queries.deleteChecklistItem(local.id)
                }
                remoteList.forEach { dto -> saveLocally(dto) }
            }
        } catch (e: Exception) {
            println("Checklist sync failed: ${e.message}")
        }
    }

    fun saveLocally(dto: ChecklistItemResponse) {
        queries.insertOrReplaceChecklistItem(
            id = dto.id,
            tripId = dto.tripId,
            title = dto.title,
            isGroup = if (dto.isGroup) 1L else 0L,
            ownerUserId = dto.ownerUserId,
            completedByJson = json.encodeToString(dto.completedBy),
            version = 1L,
        )
    }

    fun deleteLocally(itemId: String) {
        queries.deleteChecklistItem(itemId)
    }

    fun addItem(
        tripId: String,
        title: String,
        isGroup: Boolean,
    ) {
        if (!BackendFeatureFlags.CHECKLIST_ENABLED) return
        val itemId = outbox.newMutationId()
        val mutationId = outbox.newMutationId()
        val ownerUserId =
            userSession.currentUser.value
                ?.id
                .orEmpty()
        val request =
            V2CreateChecklistItemRequest(
                id = itemId,
                clientMutationId = mutationId,
                title = title,
                isGroup = isGroup,
            )

        db.transaction {
            queries.insertOrReplaceChecklistItem(
                id = itemId,
                tripId = tripId,
                title = title,
                isGroup = if (isGroup) 1L else 0L,
                ownerUserId = ownerUserId,
                completedByJson = "[]",
                version = 1L,
            )
            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.CHECKLIST,
                entityId = itemId,
                operation = OutboxOperation.CREATE,
                payloadJson =
                    json.encodeToString(
                        V2CreateChecklistItemRequest.serializer(),
                        request,
                    ),
                baseVersion = null,
                existingMutationId = mutationId,
            )
        }
        syncTrigger.requestSync()
    }

    fun deleteItem(
        tripId: String,
        itemId: String,
    ) {
        if (!BackendFeatureFlags.CHECKLIST_ENABLED) return
        val existing =
            queries.getChecklistForTrip(tripId).executeAsList().firstOrNull { it.id == itemId }
                ?: return
        val mutationId = outbox.newMutationId()

        db.transaction {
            queries.deleteChecklistItem(itemId)
            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.CHECKLIST,
                entityId = itemId,
                operation = OutboxOperation.DELETE,
                payloadJson = "{\"clientMutationId\":\"$mutationId\"}",
                baseVersion = existing.version,
                existingMutationId = mutationId,
            )
        }
        syncTrigger.requestSync()
    }

    fun toggleItem(
        tripId: String,
        itemId: String,
    ) {
        if (!BackendFeatureFlags.CHECKLIST_ENABLED) return
        val existing =
            queries.getChecklistForTrip(tripId).executeAsList().firstOrNull { it.id == itemId }
                ?: return
        val userId = userSession.currentUser.value?.id ?: return
        val current: List<String> =
            runCatching {
                json.decodeFromString<List<String>>(existing.completedByJson)
            }.getOrDefault(emptyList())
        val updated = if (userId in current) current - userId else current + userId

        val mutationId = outbox.newMutationId()

        db.transaction {
            queries.insertOrReplaceChecklistItem(
                id = existing.id,
                tripId = existing.tripId,
                title = existing.title,
                isGroup = existing.isGroup,
                ownerUserId = existing.ownerUserId,
                completedByJson = json.encodeToString(updated),
                version = existing.version,
            )
            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.CHECKLIST,
                entityId = itemId,
                operation = OutboxOperation.TOGGLE,
                payloadJson = "{\"clientMutationId\":\"$mutationId\"}",
                baseVersion = existing.version,
                existingMutationId = mutationId,
            )
        }
        syncTrigger.requestSync()
    }
}
