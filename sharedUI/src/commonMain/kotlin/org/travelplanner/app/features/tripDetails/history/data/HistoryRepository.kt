package org.travelplanner.app.features.tripDetails.history.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import org.travelplanner.app.HistoryLogEntity
import org.travelplanner.app.core.BackendFeatureFlags
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.db.MyDatabase

class HistoryRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
) {
    private val queries = db.historyQueries

    fun getLogsFlow(tripId: String): Flow<List<HistoryLogEntity>> = queries.getLogsForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    fun saveLogLocally(dto: HistoryLogDto) {
        queries.insertLog(
            id = dto.id,
            tripId = dto.tripId,
            userId = dto.userId,
            actionType = dto.actionType,
            entityType = dto.entityType,
            entityId = dto.entityId,
            details = dto.details,
            timestamp = dto.timestamp,
        )
    }

    suspend fun syncHistory(tripId: String) {
        if (!BackendFeatureFlags.HISTORY_ENABLED) return
        try {
            val remoteLogs = api.getTripHistory(tripId)
            db.transaction {
                remoteLogs.forEach { saveLogLocally(it) }
            }
        } catch (e: Exception) {
            println("History sync failed: ${e.message}")
        }
    }
}
