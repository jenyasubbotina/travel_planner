package org.travelplanner.app.features.tripDetails.more.checklist.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.travelplanner.app.TripChecklistEntity
import org.travelplanner.app.core.ChecklistItemDto
import org.travelplanner.app.core.CreateChecklistItemRequest
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.ChecklistItem
import org.travelplanner.app.domain.toDomain

class ChecklistRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
    private val json: Json,
) {
    private val queries = db.checklistsQueries

    private fun getChecklistEntityFlow(tripId: Long): Flow<List<TripChecklistEntity>> =
        queries.getChecklistForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    fun getChecklistFlow(tripId: Long): Flow<List<ChecklistItem>> =
        getChecklistEntityFlow(tripId).map { list -> list.map { it.toDomain(json) } }

    suspend fun syncChecklist(tripId: Long) {
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

    fun saveLocally(dto: ChecklistItemDto) {
        queries.insertOrReplaceChecklistItem(
            id = dto.id,
            tripId = dto.tripId,
            title = dto.title,
            isGroup = if (dto.isGroup) 1L else 0L,
            ownerUserId = dto.ownerUserId,
            completedByJson = Json.encodeToString(dto.completedBy),
        )
    }

    fun deleteLocally(itemId: String) {
        queries.deleteChecklistItem(itemId)
    }

    suspend fun addItem(
        tripId: Long,
        title: String,
        isGroup: Boolean,
    ) {
        val req = CreateChecklistItemRequest(title, isGroup)
        val created = api.addChecklistItem(tripId, req)
        saveLocally(created)
    }

    suspend fun deleteItem(
        tripId: Long,
        itemId: String,
    ) {
        api.deleteChecklistItem(tripId, itemId)
        deleteLocally(itemId)
    }

    suspend fun toggleItem(
        tripId: Long,
        itemId: String,
    ) {
        val updated = api.toggleChecklistItem(tripId, itemId)
        saveLocally(updated)
    }
}
