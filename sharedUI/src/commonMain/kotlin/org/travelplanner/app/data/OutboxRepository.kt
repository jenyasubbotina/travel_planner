package org.travelplanner.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.travelplanner.app.OutboxEntry
import org.travelplanner.app.db.MyDatabase
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object OutboxState {
    const val PENDING = "PENDING"
    const val IN_FLIGHT = "IN_FLIGHT"
    const val CONFLICT = "CONFLICT"
    const val FAILED = "FAILED"
    const val DEAD = "DEAD"
}

object OutboxEntityType {
    const val TRIP = "TRIP"
    const val EXPENSE = "EXPENSE"
    const val EVENT = "EVENT"
    const val ATTACHMENT = "ATTACHMENT"
    const val CHECKLIST = "CHECKLIST"
    const val PARTICIPANT_ROLE = "PARTICIPANT_ROLE"
    const val POINT_COMMENT = "POINT_COMMENT"
}

object OutboxOperation {
    const val CREATE = "CREATE"
    const val UPDATE = "UPDATE"
    const val DELETE = "DELETE"
    const val TOGGLE = "TOGGLE"
}

class OutboxRepository(
    private val db: MyDatabase,
    private val json: Json,
) {
    private val queries get() = db.outboxQueries

    @OptIn(ExperimentalUuidApi::class)
    fun newMutationId(): String = Uuid.random().toString()

    fun enqueue(
        tripId: String,
        entityType: String,
        entityId: String,
        operation: String,
        payloadJson: String,
        baseVersion: Long?,
        existingMutationId: String? = null,
    ): String? {
        val pending =
            queries
                .getPendingEntriesForEntity(entityType, entityId)
                .executeAsList()
                .filter { it.state == OutboxState.PENDING }

        if (pending.isNotEmpty()) {
            when (operation) {
                OutboxOperation.UPDATE -> {
                    val pendingCreate =
                        pending.firstOrNull { it.operation == OutboxOperation.CREATE }
                    if (pendingCreate != null) {
                        val mergedPayload =
                            mergePayloadFields(pendingCreate.payloadJson, payloadJson)
                        queries.updateEntryPayload(
                            mergedPayload,
                            baseVersion ?: pendingCreate.baseVersion,
                            pendingCreate.id,
                        )
                        return pendingCreate.id
                    }
                    val pendingUpdate =
                        pending.firstOrNull { it.operation == OutboxOperation.UPDATE }
                    if (pendingUpdate != null) {
                        val mergedPayload =
                            mergePayloadFields(pendingUpdate.payloadJson, payloadJson)
                        queries.updateEntryPayload(
                            mergedPayload,
                            baseVersion ?: pendingUpdate.baseVersion,
                            pendingUpdate.id,
                        )
                        return pendingUpdate.id
                    }
                }

                OutboxOperation.DELETE -> {
                    val pendingCreate =
                        pending.firstOrNull { it.operation == OutboxOperation.CREATE }
                    if (pendingCreate != null) {
                        pending.forEach { queries.deleteEntry(it.id) }
                        return null
                    }
                    val pendingUpdates = pending.filter { it.operation == OutboxOperation.UPDATE }
                    pendingUpdates.forEach { queries.deleteEntry(it.id) }
                }
            }
        }

        val nextSeq = queries.getSequence(tripId).executeAsOneOrNull() ?: 1L
        queries.upsertSequence(tripId, nextSeq + 1L)

        val id = existingMutationId ?: newMutationId()
        val now = Clock.System.now().toEpochMilliseconds()
        queries.insertEntry(
            id = id,
            tripId = tripId,
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            payloadJson = payloadJson,
            baseVersion = baseVersion,
            sequenceNumber = nextSeq,
            state = OutboxState.PENDING,
            attemptCount = 0L,
            lastError = null,
            nextRetryAt = null,
            createdAt = now,
        )
        return id
    }

    private fun mergePayloadFields(
        oldJson: String,
        newJson: String,
    ): String {
        val oldObj = runCatching { json.parseToJsonElement(oldJson).jsonObject }.getOrNull()
        val newObj = runCatching { json.parseToJsonElement(newJson).jsonObject }.getOrNull()
        if (oldObj == null || newObj == null) return newJson
        val merged = LinkedHashMap<String, JsonElement>()
        merged.putAll(oldObj)
        for ((k, v) in newObj) {
            if (v !is JsonNull) merged[k] = v
        }
        return json.encodeToString(JsonObject.serializer(), JsonObject(merged))
    }

    fun nextEligible(
        tripId: String,
        now: Long,
    ): OutboxEntry? = queries.getNextEligibleForTrip(tripId, now).executeAsOneOrNull()

    fun eligibleTripIds(now: Long): List<String> = queries.getDistinctEligibleTripIds(now).executeAsList()

    fun hasAnyPending(): Boolean = queries.hasAnyPending().executeAsOne()

    fun markInFlight(id: String) {
        queries.updateEntryState(OutboxState.IN_FLIGHT, id)
    }

    fun markSuccess(id: String) {
        queries.deleteEntry(id)
    }

    fun markFailed(
        id: String,
        error: String,
        backoffMillis: Long,
    ) {
        val nextRetryAt = Clock.System.now().toEpochMilliseconds() + backoffMillis
        queries.updateEntryFailure(OutboxState.PENDING, error, nextRetryAt, id)
    }

    fun markConflict(
        id: String,
        error: String,
    ) {
        queries.updateEntryFailure(OutboxState.CONFLICT, error, null, id)
    }

    fun markPendingForRebase(
        id: String,
        payloadJson: String,
        baseVersion: Long?,
    ) {
        queries.updateEntryRebase(payloadJson, baseVersion, id)
    }

    fun markCancelled(
        id: String,
        error: String,
    ) {
        queries.updateEntryCancelled(error, id)
    }

    fun deleteEntry(id: String) {
        queries.deleteEntry(id)
    }

    fun markRetry(id: String) {
        queries.updateEntryRetry(id)
    }

    fun updatePayloadOnly(
        id: String,
        payloadJson: String,
    ) {
        queries.updateEntryPayloadOnly(payloadJson, id)
    }

    fun getEntriesForEntityByState(
        entityType: String,
        entityId: String,
        state: String,
    ): List<OutboxEntry> = queries.getEntriesForEntityByState(entityType, entityId, state).executeAsList()

    fun markDead(
        id: String,
        error: String,
    ) {
        queries.updateEntryFailure(OutboxState.DEAD, error, null, id)
    }

    fun reclaimInFlight() {
        queries.reclaimInFlight()
    }

    fun clearAllBackoff() {
        queries.clearAllBackoff()
    }

    fun observeConflicts(tripId: String): Flow<List<OutboxEntry>> =
        queries
            .getEntriesForTripByState(tripId, OutboxState.CONFLICT)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun observeDead(tripId: String): Flow<List<OutboxEntry>> =
        queries
            .getEntriesForTripByState(tripId, OutboxState.DEAD)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun observePendingCount(tripId: String): Flow<Long> =
        queries
            .countPendingForTrip(tripId)
            .asFlow()
            .mapToOne(Dispatchers.IO)

    fun observePendingCount(): Flow<Long> =
        queries
            .countAllPending()
            .asFlow()
            .mapToOne(Dispatchers.IO)

    fun observeDepthAlert(tripId: String): Flow<Boolean> =
        queries
            .countAllNonSuccessForTrip(tripId)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { count -> count > DEPTH_CAP }

    fun getEntry(id: String): OutboxEntry? = queries.getEntryById(id).executeAsOneOrNull()

    fun deleteAllForTrip(tripId: String) {
        db.transaction {
            queries.deleteEntriesForTrip(tripId)
            queries.deleteSequenceForTrip(tripId)
        }
    }

    fun deleteAll() {
        db.transaction {
            queries.deleteAllEntries()
            queries.deleteAllSequences()
        }
    }

    companion object {
        const val DEPTH_CAP = 1000L
    }
}
