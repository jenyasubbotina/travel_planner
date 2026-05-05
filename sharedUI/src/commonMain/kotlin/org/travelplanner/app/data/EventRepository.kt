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
import org.travelplanner.app.TripEventEntity
import org.travelplanner.app.core.AddPointCommentRequest
import org.travelplanner.app.core.AddPointLinkRequest
import org.travelplanner.app.core.BackendFeatureFlags
import org.travelplanner.app.core.ItineraryPointResponse
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.V2CreateItineraryPointRequest
import org.travelplanner.app.core.V2UpdateItineraryPointRequest
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.Event
import org.travelplanner.app.domain.toDomain
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventCommentDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventFileDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventLinkDto
import kotlin.time.Clock

internal fun mimeTypeForFileName(fileName: String): String =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        else -> "application/octet-stream"
    }

class EventRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
    private val outbox: OutboxRepository,
    private val attachmentStorage: OutboxAttachmentStorage,
    private val userSession: UserSession,
    private val syncTrigger: SyncTrigger,
    private val json: Json,
) {
    private val queries = db.eventsQueries

    private fun getEventsEntityFlow(tripId: String): Flow<List<TripEventEntity>> =
        queries.getEventsForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    private fun getEventEntityFlow(eventId: String): Flow<TripEventEntity?> =
        queries.getEventById(eventId).asFlow().mapToOneOrNull(Dispatchers.IO)

    fun getEventsFlow(tripId: String): Flow<List<Event>> = getEventsEntityFlow(tripId).map { list -> list.map { it.toDomain(json) } }

    fun getEventFlow(eventId: String): Flow<Event?> = getEventEntityFlow(eventId).map { it?.toDomain(json) }

    fun saveEventFromResponse(
        tripId: String,
        response: ItineraryPointResponse,
    ) {
        db.transaction {
            val existing = queries.getEventById(response.id).executeAsOneOrNull()
            val resolvedCategory =
                response.category ?: response.type ?: existing?.category ?: "OTHER"
            val participantsJson = json.encodeToString(response.participantIds)
            val linksJson = existing?.linksJson ?: "[]"
            val commentsJson = existing?.commentsJson ?: "[]"
            val filesJson = existing?.filesJson ?: "[]"

            if (existing != null) {
                queries.updateEventFull(
                    tripId = tripId,
                    dayIndex = response.dayIndex.toLong(),
                    time = response.startTime ?: "",
                    title = response.title,
                    subtitle = response.subtitle ?: existing.subtitle,
                    description = response.description,
                    duration = response.duration,
                    cost = response.cost ?: existing.cost ?: 0.0,
                    actualCost = response.actualCost ?: existing.actualCost ?: 0.0,
                    status = response.status,
                    category = resolvedCategory,
                    latitude = response.latitude ?: 0.0,
                    longitude = response.longitude ?: 0.0,
                    address = response.address,
                    linksJson = linksJson,
                    commentsJson = commentsJson,
                    filesJson = filesJson,
                    participantIdsJson = participantsJson,
                    type = response.type,
                    date = response.date,
                    startTime = response.startTime,
                    endTime = response.endTime,
                    sortOrder = response.sortOrder.toLong(),
                    version = response.version,
                    createdBy = response.createdBy,
                    id = response.id,
                )
            } else {
                queries.insertOrReplaceEvent(
                    id = response.id,
                    tripId = tripId,
                    dayIndex = response.dayIndex.toLong(),
                    time = response.startTime ?: "",
                    title = response.title,
                    subtitle = response.subtitle ?: "",
                    description = response.description,
                    duration = response.duration,
                    cost = response.cost ?: 0.0,
                    actualCost = response.actualCost ?: 0.0,
                    status = response.status,
                    category = resolvedCategory,
                    latitude = response.latitude ?: 0.0,
                    longitude = response.longitude ?: 0.0,
                    address = response.address,
                    linksJson = linksJson,
                    commentsJson = commentsJson,
                    filesJson = filesJson,
                    participantIdsJson = participantsJson,
                    type = response.type,
                    date = response.date,
                    startTime = response.startTime,
                    endTime = response.endTime,
                    sortOrder = response.sortOrder.toLong(),
                    version = response.version,
                    createdBy = response.createdBy,
                )
            }
        }
    }

    suspend fun syncEvents(tripId: String) {
        try {
            val remoteEvents = api.getItinerary(tripId)
            val remoteIds = remoteEvents.map { it.id }.toSet()

            db.transaction {
                val localEvents = queries.getEventsForTrip(tripId).executeAsList()
                localEvents.forEach { local ->
                    if (local.id !in remoteIds) {
                        queries.deleteEvent(local.id)
                    }
                }
                remoteEvents.forEach { response ->
                    saveEventFromResponse(tripId, response)
                }
            }
        } catch (e: Exception) {
            println("Event sync failed (Offline mode): ${e.message}")
        }
    }

    fun addEvent(
        tripId: String,
        dto: EventDto,
    ): String {
        val eventId = outbox.newMutationId()
        val mutationId = outbox.newMutationId()
        val request =
            V2CreateItineraryPointRequest(
                id = eventId,
                clientMutationId = mutationId,
                title = dto.title,
                description = dto.description,
                subtitle = dto.subtitle.ifBlank { null },
                type = dto.category,
                category = dto.category,
                date = null,
                dayIndex = dto.dayIndex,
                startTime = dto.time.ifBlank { null },
                duration = dto.duration?.ifBlank { null },
                latitude = if (dto.latitude != 0.0) dto.latitude else null,
                longitude = if (dto.longitude != 0.0) dto.longitude else null,
                address = dto.address,
                cost = dto.cost.takeIf { it > 0.0 },
                actualCost = dto.actualCost?.takeIf { it > 0.0 },
                status = dto.status.takeIf { it.isNotBlank() && it != "NONE" },
                participantIds = dto.participantIds.takeIf { it.isNotEmpty() },
            )

        db.transaction {
            queries.insertOrReplaceEvent(
                id = eventId,
                tripId = tripId,
                dayIndex = dto.dayIndex.toLong(),
                time = dto.time,
                title = dto.title,
                subtitle = dto.subtitle,
                description = dto.description,
                duration = dto.duration,
                cost = dto.cost,
                actualCost = dto.actualCost ?: 0.0,
                status = dto.status,
                category = dto.category,
                latitude = dto.latitude,
                longitude = dto.longitude,
                address = dto.address,
                linksJson = json.encodeToString(dto.links),
                commentsJson = json.encodeToString(dto.comments),
                filesJson = json.encodeToString(dto.files),
                participantIdsJson = json.encodeToString(dto.participantIds),
                type = dto.category,
                date = null,
                startTime = dto.time.ifBlank { null },
                endTime = null,
                sortOrder = 0L,
                version = 0L,
                createdBy =
                    userSession.currentUser.value
                        ?.id
                        .orEmpty(),
            )

            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.EVENT,
                entityId = eventId,
                operation = OutboxOperation.CREATE,
                payloadJson =
                    json.encodeToString(
                        V2CreateItineraryPointRequest.serializer(),
                        request,
                    ),
                baseVersion = null,
                existingMutationId = mutationId,
            )
        }
        syncTrigger.requestSync()
        return eventId
    }

    fun updateEvent(
        tripId: String,
        eventId: String,
        dto: EventDto,
    ) {
        val existing = queries.getEventById(eventId).executeAsOneOrNull() ?: return
        val mutationId = outbox.newMutationId()
        val request =
            V2UpdateItineraryPointRequest(
                clientMutationId = mutationId,
                title = dto.title,
                description = dto.description,
                subtitle = dto.subtitle,
                type = dto.category,
                category = dto.category,
                dayIndex = dto.dayIndex,
                startTime = dto.time.ifBlank { null },
                duration = dto.duration,
                latitude = if (dto.latitude != 0.0) dto.latitude else null,
                longitude = if (dto.longitude != 0.0) dto.longitude else null,
                address = dto.address,
                cost = dto.cost,
                actualCost = dto.actualCost,
                status = dto.status,
                participantIds = dto.participantIds,
                expectedVersion = existing.version,
            )

        db.transaction {
            queries.updateEventFull(
                tripId = tripId,
                dayIndex = dto.dayIndex.toLong(),
                time = dto.time,
                title = dto.title,
                subtitle = dto.subtitle,
                description = dto.description,
                duration = dto.duration,
                cost = dto.cost,
                actualCost = dto.actualCost ?: existing.actualCost ?: 0.0,
                status = dto.status,
                category = dto.category,
                latitude = dto.latitude,
                longitude = dto.longitude,
                address = dto.address,
                linksJson = existing.linksJson ?: "[]",
                commentsJson = existing.commentsJson ?: "[]",
                filesJson = existing.filesJson ?: "[]",
                participantIdsJson = json.encodeToString(dto.participantIds),
                type = dto.category,
                date = existing.date,
                startTime = dto.time.ifBlank { null },
                endTime = existing.endTime,
                sortOrder = existing.sortOrder,
                version = existing.version,
                createdBy = existing.createdBy,
                id = eventId,
            )

            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.EVENT,
                entityId = eventId,
                operation = OutboxOperation.UPDATE,
                payloadJson =
                    json.encodeToString(
                        V2UpdateItineraryPointRequest.serializer(),
                        request,
                    ),
                baseVersion = existing.version,
                existingMutationId = mutationId,
            )
        }
        syncTrigger.requestSync()
    }

    fun enqueueFileAttachment(
        tripId: String,
        eventId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
    ) {
        val attachmentId = outbox.newMutationId()
        val absolutePath =
            runCatching { attachmentStorage.write(attachmentId, fileBytes) }
                .getOrNull() ?: return
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
                eventId,
                userSession.currentUser.value
                    ?.id
                    .orEmpty(),
                fileName,
                fileBytes.size.toLong(),
                mimeType,
                "pending://$absolutePath",
                now,
            )

            val payload =
                AttachmentOutboxPayload(
                    attachmentId = attachmentId,
                    tripId = tripId,
                    expenseId = null,
                    pointId = eventId,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSizeBytes = fileBytes.size.toLong(),
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

            reconcileFilesJsonFromAttachmentsInTransaction(eventId)
        }
        syncTrigger.requestSync()
    }

    fun deleteEventLocal(id: String) = queries.deleteEvent(id)

    fun deleteEvent(
        tripId: String,
        eventId: String,
    ) {
        if (eventId.isBlank()) return
        val existing = queries.getEventById(eventId).executeAsOneOrNull() ?: return
        val mutationId = outbox.newMutationId()
        val payloadJson =
            json.encodeToString(
                V2UpdateItineraryPointRequest.serializer(),
                V2UpdateItineraryPointRequest(
                    clientMutationId = mutationId,
                    expectedVersion = existing.version,
                ),
            )

        db.transaction {
            queries.deleteEvent(eventId)
            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.EVENT,
                entityId = eventId,
                operation = OutboxOperation.DELETE,
                payloadJson = payloadJson,
                baseVersion = existing.version,
                existingMutationId = mutationId,
            )
        }
        syncTrigger.requestSync()
    }

    fun reconcileFilesJsonFromAttachments(eventId: String) {
        db.transaction {
            reconcileFilesJsonFromAttachmentsInTransaction(eventId)
        }
    }

    internal fun reconcileFilesJsonFromAttachmentsInTransaction(eventId: String) {
        val entity =
            queries.getEventById(eventId).executeAsOneOrNull()
                ?: return
        val files =
            db.attachmentsQueries
                .getAttachmentsForPoint(eventId)
                .executeAsList()
                .sortedBy { it.createdAt }
                .map { att ->
                    EventFileDto(
                        name = att.fileName,
                        url = att.s3Key,
                        type = if (att.mimeType.startsWith("image/")) "PHOTO" else "DOCUMENT",
                    )
                }
        queries.updateEvent(
            time = entity.time,
            title = entity.title,
            subtitle = entity.subtitle,
            description = entity.description,
            cost = entity.cost,
            actualCost = entity.actualCost,
            category = entity.category,
            address = entity.address,
            linksJson = entity.linksJson,
            commentsJson = entity.commentsJson,
            filesJson = json.encodeToString(files),
            participantIdsJson = entity.participantIdsJson,
            id = eventId,
        )
    }

    suspend fun refreshPointFiles(
        tripId: String,
        eventId: String,
    ) {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return
        try {
            val files =
                api
                    .listTripFiles(tripId, scope = "all")
                    .filter { it.pointId == eventId }
                    .sortedBy { it.createdAt }
                    .map {
                        EventFileDto(
                            name = it.fileName,
                            url = it.s3Key,
                            type = if (it.mimeType.startsWith("image/")) "PHOTO" else "DOCUMENT",
                        )
                    }

            db.transaction {
                val entity =
                    queries.getEventById(eventId).executeAsOneOrNull()
                        ?: return@transaction
                queries.updateEvent(
                    time = entity.time,
                    title = entity.title,
                    subtitle = entity.subtitle,
                    description = entity.description,
                    cost = entity.cost,
                    actualCost = entity.actualCost,
                    category = entity.category,
                    address = entity.address,
                    linksJson = entity.linksJson,
                    commentsJson = entity.commentsJson,
                    filesJson = json.encodeToString(files),
                    participantIdsJson = entity.participantIdsJson,
                    id = eventId,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun addPointLink(
        tripId: String,
        eventId: String,
        title: String,
        url: String,
    ) {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return
        val response = api.addPointLink(tripId, eventId, AddPointLinkRequest(title, url)) ?: return
        appendLinkLocally(eventId, EventLinkDto(title = response.title, url = response.url))
    }

    suspend fun deletePointLink(
        tripId: String,
        eventId: String,
        linkId: String,
    ) {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return
        api.deletePointLink(tripId, eventId, linkId)
    }

    fun addPointComment(
        tripId: String,
        eventId: String,
        text: String,
    ) {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return
        val commentId = outbox.newMutationId()
        val mutationId = outbox.newMutationId()
        val user = userSession.currentUser.value
        val request =
            AddPointCommentRequest(
                id = commentId,
                clientMutationId = mutationId,
                text = text,
            )
        val now = Clock.System.now().toEpochMilliseconds()
        val localComment =
            EventCommentDto(
                userId = user?.id.orEmpty(),
                userName = user?.name.orEmpty(),
                text = text,
                timestamp = now,
            )

        db.transaction {
            appendCommentLocally(eventId, localComment)
            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.POINT_COMMENT,
                entityId = commentId,
                operation = OutboxOperation.CREATE,
                payloadJson =
                    json
                        .encodeToString(AddPointCommentRequest.serializer(), request)
                        .let { wrap -> wrapWithPointId(wrap, eventId) },
                baseVersion = null,
                existingMutationId = mutationId,
            )
        }
        syncTrigger.requestSync()
    }

    private fun wrapWithPointId(
        payloadJson: String,
        pointId: String,
    ): String {
        val obj =
            json.parseToJsonElement(payloadJson).let {
                if (it is kotlinx.serialization.json.JsonObject) {
                    kotlinx.serialization.json.JsonObject(
                        it.toMutableMap().apply {
                            put("__pointId", kotlinx.serialization.json.JsonPrimitive(pointId))
                        },
                    )
                } else {
                    it
                }
            }
        return json.encodeToString(
            kotlinx.serialization.json.JsonElement
                .serializer(),
            obj,
        )
    }

    suspend fun uploadPointFile(
        tripId: String,
        eventId: String,
        fileBytes: ByteArray,
        fileName: String,
        mimeType: String,
    ) {
        if (!BackendFeatureFlags.RICH_EVENT_DATA_ENABLED) return
        enqueueFileAttachment(tripId, eventId, fileBytes, fileName, mimeType)
    }

    private fun appendLinkLocally(
        eventId: String,
        link: EventLinkDto,
    ) {
        db.transaction {
            val entity =
                queries.getEventById(eventId).executeAsOneOrNull()
                    ?: return@transaction
            val current: List<EventLinkDto> =
                runCatching {
                    json.decodeFromString<List<EventLinkDto>>(entity.linksJson ?: "[]")
                }.getOrDefault(emptyList())
            val updated = current + link
            queries.updateEvent(
                time = entity.time,
                title = entity.title,
                subtitle = entity.subtitle,
                description = entity.description,
                cost = entity.cost,
                actualCost = entity.actualCost,
                category = entity.category,
                address = entity.address,
                linksJson = json.encodeToString(updated),
                commentsJson = entity.commentsJson,
                filesJson = entity.filesJson,
                participantIdsJson = entity.participantIdsJson,
                id = eventId,
            )
        }
    }

    private fun appendCommentLocally(
        eventId: String,
        comment: EventCommentDto,
    ) {
        val entity =
            queries.getEventById(eventId).executeAsOneOrNull()
                ?: return
        val current: List<EventCommentDto> =
            runCatching {
                json.decodeFromString<List<EventCommentDto>>(entity.commentsJson ?: "[]")
            }.getOrDefault(emptyList())
        val updated = current + comment
        queries.updateEvent(
            time = entity.time,
            title = entity.title,
            subtitle = entity.subtitle,
            description = entity.description,
            cost = entity.cost,
            actualCost = entity.actualCost,
            category = entity.category,
            address = entity.address,
            linksJson = entity.linksJson,
            commentsJson = json.encodeToString(updated),
            filesJson = entity.filesJson,
            participantIdsJson = entity.participantIdsJson,
            id = eventId,
        )
    }
}
