package org.travelplanner.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.travelplanner.app.core.DeltaResponse
import org.travelplanner.app.core.HistoryEntryResponse
import org.travelplanner.app.core.PointCommentResponse
import org.travelplanner.app.core.PointLinkResponse
import org.travelplanner.app.core.SnapshotResponse
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.TripResponse
import org.travelplanner.app.core.TripUtils.isoToEpochMillis
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventCommentDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventLinkDto
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Clock

enum class NetworkState { CONNECTING, ONLINE, OFFLINE }

class GlobalSyncManager(
    private val userSession: UserSession,
    private val api: TripApiService,
    private val tripRepo: TripRepository,
    private val participantRepo: ParticipantRepository,
    private val expenseRepo: ExpenseRepository,
    private val eventRepo: EventRepository,
    private val db: MyDatabase,
    private val syncTrigger: SyncTrigger,
    private val json: Json,
) {
    private val _networkState = MutableStateFlow(NetworkState.OFFLINE)
    val networkState = _networkState.asStateFlow()

    private val _retryCountdown = MutableStateFlow<Int?>(null)
    val retryCountdown = _retryCountdown.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var syncJob: Job? = null

    private val cursorQueries get() = db.syncCursorsQueries
    private val checklistQueries get() = db.checklistsQueries
    private val historyQueries get() = db.historyQueries
    private val joinRequestQueries get() = db.joinRequestsQueries
    private val eventsQueries get() = db.eventsQueries

    private var knownTrips: List<TripResponse> = emptyList()

    init {
        scope.launch {
            networkState.collect { state ->
                val isOffline = state != NetworkState.ONLINE
                api.isOffline = isOffline
            }
        }

        scope.launch {
            userSession.currentUser
                .distinctUntilChangedBy { it?.id }
                .collect { user ->
                    syncJob?.cancel()
                    syncJob = null
                    knownTrips = emptyList()
                    if (user != null) {
                        syncJob = scope.launch { startSyncLoop() }
                    } else {
                        val deviceId = api.currentDeviceId
                        if (deviceId != null) {
                            try {
                                api.removeDevice(deviceId)
                            } catch (_: Exception) {
                            }
                            api.currentDeviceId = null
                        }
                        _networkState.value = NetworkState.OFFLINE
                    }
                }
        }
    }

    private suspend fun startSyncLoop() {
        _networkState.value = NetworkState.CONNECTING
        _retryCountdown.value = null
        var consecutiveFailures = 0

        while (true) {
            val healthy =
                try {
                    api.healthCheck()
                } catch (_: Exception) {
                    false
                }
            if (healthy) break

            consecutiveFailures++
            _networkState.value = NetworkState.OFFLINE

            val baseDelay = (2000L * 2.0.pow(consecutiveFailures.toDouble())).toLong()
            val cappedDelay = baseDelay.coerceAtMost(30_000L)
            val jitter = Random.nextLong(0, 1000)
            val finalDelay = cappedDelay + jitter

            var remaining = (finalDelay / 1000).toInt()
            while (remaining > 0) {
                _retryCountdown.value = remaining
                delay(1000)
                remaining--
            }
            _retryCountdown.value = null
            _networkState.value = NetworkState.CONNECTING
        }

        _networkState.value = NetworkState.ONLINE
        api.isOffline = false
        _retryCountdown.value = null
        consecutiveFailures = 0

        try {
            performFullSync()
        } catch (e: Exception) {
            println("Initial sync failed: ${e.message}")
        }

        while (true) {
            val triggered =
                withTimeoutOrNull(5 * 60 * 1000L) {
                    syncTrigger.signal.receive()
                    true
                } ?: false

            try {
                val healthy =
                    try {
                        api.healthCheck()
                    } catch (_: Exception) {
                        false
                    }
                if (!healthy) {
                    _networkState.value = NetworkState.OFFLINE
                    api.isOffline = true
                    return startSyncLoop()
                }

                if (triggered || knownTrips.isEmpty()) {
                    knownTrips = emptyList()
                    performFullSync()
                } else {
                    performDeltaSync()
                }
            } catch (e: Exception) {
                println("Sync cycle failed: ${e.message}")
            }
        }
    }

    private suspend fun performFullSync() {
        knownTrips = api.getTrips()
        tripRepo.saveSyncedTrips(knownTrips)

        for (trip in knownTrips) {
            try {
                val storedCursor =
                    cursorQueries
                        .getCursor(trip.id)
                        .executeAsOneOrNull()
                if (storedCursor == null) {
                    println("[Sync] tripId=${trip.id} no cursor → snapshot")
                    val snapshot = api.getSnapshot(trip.id)
                    logSnapshot(snapshot)
                    applySnapshot(trip.id, snapshot)
                    persistCursor(trip.id, snapshot.cursor, trip.version)
                } else {
                    println("[Sync] tripId=${trip.id} cursor=$storedCursor → delta")
                    val delta = api.getDelta(trip.id, storedCursor)
                    logDelta(delta)
                    applyDelta(trip.id, delta)
                    persistCursor(trip.id, delta.cursor, trip.version)
                }
            } catch (e: Exception) {
                println("[Sync] FAIL tripId=${trip.id}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private suspend fun performDeltaSync() {
        for (trip in knownTrips) {
            try {
                val storedCursor =
                    cursorQueries
                        .getCursor(trip.id)
                        .executeAsOneOrNull() ?: continue
                println("[Sync] (fallback) tripId=${trip.id} cursor=$storedCursor → delta")
                val delta = api.getDelta(trip.id, storedCursor)
                logDelta(delta)
                applyDelta(trip.id, delta)
                persistCursor(trip.id, delta.cursor, trip.version)
            } catch (e: Exception) {
                println("[Sync] FAIL tripId=${trip.id}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun logSnapshot(s: SnapshotResponse) {
        println(
            "[Sync] snapshot: trip=${s.trip.id} participants=${s.participants.size} " +
                "points=${s.itineraryPoints.size} expenses=${s.expenses.size} " +
                "attachments=${s.attachments.size} checklist=${s.checklistItems.size} " +
                "pendingJoinRequests=${s.pendingJoinRequests.size} history=${s.historyEntries.size} " +
                "pointLinks=${s.pointLinks.size} pointComments=${s.pointComments.size} cursor=${s.cursor}",
        )
    }

    private fun logDelta(d: DeltaResponse) {
        println(
            "[Sync] delta: trips=${d.trips.size} participants=${d.participants.size} " +
                "points=${d.itineraryPoints.size} expenses=${d.expenses.size} " +
                "attachments=${d.attachments.size} checklist=${d.checklistItems.size} " +
                "pendingJoinRequests=${d.pendingJoinRequests.size} history=${d.historyEntries.size} " +
                "pointLinks=${d.pointLinks.size} pointComments=${d.pointComments.size} cursor=${d.cursor}",
        )
        if (d.checklistItems.isNotEmpty()) {
            d.checklistItems.forEach {
                println("[Sync]   checklist item: id=${it.id} title='${it.title}' isGroup=${it.isGroup} owner=${it.ownerUserId}")
            }
        }
    }

    private fun persistCursor(
        tripId: String,
        cursor: String,
        tripVersion: Long,
    ) {
        cursorQueries.upsertCursor(
            tripId = tripId,
            cursor = cursor,
            tripVersion = tripVersion,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )
    }

    private suspend fun applySnapshot(
        tripId: String,
        snapshot: SnapshotResponse,
    ) {
        participantRepo.syncParticipants(tripId)

        val snapshotEventIds = snapshot.itineraryPoints.map { it.id }.toSet()
        val snapshotExpenseIds = snapshot.expenses.map { it.id }.toSet()
        val snapshotAttachmentIds = snapshot.attachments.map { it.id }.toSet()
        val snapshotChecklistIds = snapshot.checklistItems.map { it.id }.toSet()
        val snapshotJoinRequestIds = snapshot.pendingJoinRequests.map { it.userId }.toSet()
        val snapshotPointIds = snapshot.itineraryPoints.map { it.id }.toSet()

        snapshot.itineraryPoints.forEach { point ->
            eventRepo.saveEventFromResponse(tripId, point)
        }

        snapshot.expenses.forEach { expense ->
            expenseRepo.upsertExpenseFromResponse(tripId, expense)
        }

        snapshot.attachments.forEach { att ->
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

        snapshot.itineraryPoints.forEach { point ->
            eventRepo.reconcileFilesJsonFromAttachments(point.id)
        }

        db.transaction {
            snapshot.checklistItems.forEach { item ->
                checklistQueries.insertOrReplaceChecklistItem(
                    id = item.id,
                    tripId = item.tripId,
                    title = item.title,
                    isGroup = if (item.isGroup) 1L else 0L,
                    ownerUserId = item.ownerUserId,
                    completedByJson = json.encodeToString(item.completedBy),
                )
            }
        }

        db.transaction {
            snapshot.historyEntries.forEach { it.upsertLocal() }
        }

        db.transaction {
            snapshot.pendingJoinRequests.forEach { req ->
                joinRequestQueries.insertOrReplaceJoinRequest(
                    id = req.userId,
                    tripId = tripId,
                    requesterUserId = req.userId,
                    displayName = req.displayName,
                    email = req.email,
                    createdAt = "",
                )
            }
        }

        applyPointDetails(
            tripIds = snapshotPointIds,
            links = snapshot.pointLinks,
            comments = snapshot.pointComments,
            authoritativeForAllPoints = true,
        )

        val localEventRemoteIds = db.eventsQueries.getRemoteIdsForTrip(tripId).executeAsList()
        localEventRemoteIds.filter { it !in snapshotEventIds }.forEach { orphanId ->
            eventRepo.deleteEventByRemoteId(orphanId)
        }

        val localExpenseRemoteIds = db.expensesQueries.getRemoteIdsForTrip(tripId).executeAsList()
        localExpenseRemoteIds.filter { it !in snapshotExpenseIds }.forEach { orphanId ->
            expenseRepo.deleteExpenseByRemoteIdLocal(orphanId, tripId)
        }

        val localAttachmentIds =
            db.attachmentsQueries.getAttachmentIdsByTrip(tripId).executeAsList()
        localAttachmentIds.filter { it !in snapshotAttachmentIds }.forEach { orphanId ->
            db.attachmentsQueries.deleteAttachment(orphanId)
        }
        val localChecklistIds =
            checklistQueries.getChecklistForTrip(tripId).executeAsList().map { it.id }
        localChecklistIds.filter { it !in snapshotChecklistIds }.forEach { orphanId ->
            checklistQueries.deleteChecklistItem(orphanId)
        }

        val localJoinRequestIds =
            joinRequestQueries.getJoinRequestIdsForTrip(tripId).executeAsList()
        localJoinRequestIds.filter { it !in snapshotJoinRequestIds }.forEach { orphanId ->
            joinRequestQueries.deleteJoinRequest(orphanId)
        }
    }

    private suspend fun applyDelta(
        tripId: String,
        delta: DeltaResponse,
    ) {
        delta.itineraryPoints.forEach { point ->
            if (point.deletedAt != null) {
                eventRepo.deleteEventByRemoteId(point.id)
            } else {
                eventRepo.saveEventFromResponse(tripId, point)
            }
        }

        delta.expenses.forEach { expense ->
            if (expense.deletedAt != null) {
                expenseRepo.deleteExpenseByRemoteIdLocal(expense.id, tripId)
            } else {
                expenseRepo.upsertExpenseFromResponse(tripId, expense)
            }
        }

        var needsFullParticipantSync = false
        delta.participants.forEach { participant ->
            if (participant.role == "LEFT") {
                participantRepo.handleParticipantLeft(tripId, participant.userId)
            } else {
                val existing =
                    db.participantsQueries
                        .getParticipantByUserId(tripId, participant.userId)
                        .executeAsOneOrNull()
                if (existing != null) {
                    db.participantsQueries.updateParticipantRole(
                        participant.role,
                        tripId,
                        participant.userId,
                    )
                } else {
                    needsFullParticipantSync = true
                }
            }
        }

        if (needsFullParticipantSync) {
            participantRepo.syncParticipants(tripId)
        }

        delta.attachments.forEach { att ->
            if (att.deletedAt != null) {
                db.attachmentsQueries.deleteAttachment(att.id)
            } else {
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
        }

        val affectedPointIds =
            (
                delta.itineraryPoints.mapNotNull { it.id.takeIf { id -> id.isNotBlank() } } +
                    delta.attachments.mapNotNull { it.pointId }
            ).toSet()
        affectedPointIds.forEach { pointId ->
            eventRepo.reconcileFilesJsonFromAttachments(pointId)
        }

        db.transaction {
            delta.checklistItems.forEach { item ->
                checklistQueries.insertOrReplaceChecklistItem(
                    id = item.id,
                    tripId = item.tripId,
                    title = item.title,
                    isGroup = if (item.isGroup) 1L else 0L,
                    ownerUserId = item.ownerUserId,
                    completedByJson = json.encodeToString(item.completedBy),
                )
            }
        }

        db.transaction {
            delta.historyEntries.forEach { it.upsertLocal() }
        }

        db.transaction {
            delta.pendingJoinRequests.forEach { req ->
                joinRequestQueries.insertOrReplaceJoinRequest(
                    id = req.userId,
                    tripId = tripId,
                    requesterUserId = req.userId,
                    displayName = req.displayName,
                    email = req.email,
                    createdAt = "",
                )
            }
        }

        if (delta.pointLinks.isNotEmpty() || delta.pointComments.isNotEmpty()) {
            val touchedPointIds =
                (delta.pointLinks.map { it.pointId } + delta.pointComments.map { it.pointId })
                    .toSet()
            applyPointDetails(
                tripIds = touchedPointIds,
                links = delta.pointLinks,
                comments = delta.pointComments,
                authoritativeForAllPoints = false,
            )
        }

        delta.trips.forEach { trip ->
            if (trip.deletedAt != null) {
                tripRepo.deleteTripCascade(trip.id)
                knownTrips = emptyList()
            } else {
                tripRepo.applyServerTripDelta(trip)
            }
        }
    }

    private fun applyPointDetails(
        tripIds: Set<String>,
        links: List<PointLinkResponse>,
        comments: List<PointCommentResponse>,
        authoritativeForAllPoints: Boolean,
    ) {
        val linksByPoint =
            links
                .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
                .groupBy { it.pointId }
        val commentsByPoint =
            comments
                .sortedBy { it.createdAt }
                .groupBy { it.pointId }

        val pointsToUpdate =
            if (authoritativeForAllPoints) {
                tripIds
            } else {
                (linksByPoint.keys + commentsByPoint.keys)
            }

        if (pointsToUpdate.isEmpty()) return

        db.transaction {
            for (pointId in pointsToUpdate) {
                val existingId =
                    eventsQueries.getEventIdByRemoteId(pointId).executeAsOneOrNull()
                        ?: continue
                val entity =
                    eventsQueries.getEventById(existingId).executeAsOneOrNull()
                        ?: continue

                val linksDto =
                    linksByPoint[pointId]?.map { EventLinkDto(title = it.title, url = it.url) }
                val commentsDto =
                    commentsByPoint[pointId]?.map {
                        EventCommentDto(
                            userId = it.authorUserId,
                            userName = it.authorDisplayName,
                            text = it.text,
                            timestamp = runCatching { isoToEpochMillis(it.createdAt) }.getOrDefault(0L),
                        )
                    }

                val newLinksJson =
                    if (linksDto != null) {
                        json.encodeToString(linksDto)
                    } else if (authoritativeForAllPoints) {
                        "[]"
                    } else {
                        entity.linksJson ?: "[]"
                    }

                val newCommentsJson =
                    if (commentsDto != null) {
                        json.encodeToString(commentsDto)
                    } else if (authoritativeForAllPoints) {
                        "[]"
                    } else {
                        entity.commentsJson ?: "[]"
                    }

                eventsQueries.updateEvent(
                    time = entity.time,
                    title = entity.title,
                    subtitle = entity.subtitle,
                    description = entity.description,
                    cost = entity.cost,
                    actualCost = entity.actualCost,
                    category = entity.category,
                    address = entity.address,
                    linksJson = newLinksJson,
                    commentsJson = newCommentsJson,
                    filesJson = entity.filesJson,
                    participantIdsJson = entity.participantIdsJson,
                    id = existingId,
                )
            }
        }
    }

    private fun HistoryEntryResponse.upsertLocal() {
        historyQueries.insertLog(
            id = id,
            tripId = tripId,
            userId = userId,
            actionType = actionType,
            entityType = entityType,
            entityId = entityId,
            details = details,
            timestamp = timestamp,
        )
    }

    fun syncNow() {
        syncTrigger.requestSync()
    }

    fun forceFullRefresh() {
        knownTrips = emptyList()
        syncTrigger.requestSync()
    }

    fun markOnline() {
        _networkState.value = NetworkState.ONLINE
    }

    fun stopGlobalSync() {
        syncJob?.cancel()
        syncJob = null
    }

    fun onAppResumed() {
        if (_networkState.value == NetworkState.ONLINE ||
            _networkState.value == NetworkState.CONNECTING
        ) {
            knownTrips = emptyList()
            syncNow()
            return
        }
        _retryCountdown.value = null
        syncJob?.cancel()
        syncJob = scope.launch { startSyncLoop() }
    }
}
