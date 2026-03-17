package org.travelplanner.app.domain

import kotlinx.serialization.json.Json
import org.travelplanner.app.ExpenseSplitEntity
import org.travelplanner.app.TripChecklistEntity
import org.travelplanner.app.TripEntity
import org.travelplanner.app.TripEventEntity
import org.travelplanner.app.TripExpenseEntity
import org.travelplanner.app.TripParticipantEntity
import org.travelplanner.app.core.UserDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventCommentDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventFileDto
import org.travelplanner.app.features.tripDetails.route.detailed.data.EventLinkDto

fun TripEntity.toDomain(): Trip =
    Trip(
        id = id,
        title = title,
        destination = destination,
        startDate = startDate,
        endDate = endDate,
        currency = currency,
        totalBudget = totalBudget,
        spentAmount = spentAmount,
        description = description,
        participantCount = participantCount.toInt(),
        status = status,
        joinCode = joinCode,
        ownerUserId = ownerUserId,
        imageUrl = imageUrl,
        filesJson = filesJson,
    )

fun TripParticipantEntity.toDomain(): Participant =
    Participant(
        id = id,
        tripId = tripId,
        userId = userId,
        name = name,
        email = email,
        role = role,
        avatarColor1 = avatarColor1,
        avatarColor2 = avatarColor2,
    )

fun TripExpenseEntity.toDomain(): Expense =
    Expense(
        id = id,
        remoteId = remoteId,
        tripId = tripId,
        title = title,
        amount = amount,
        category = category,
        payerName = payerName,
        date = date,
        splitDescription = splitDescription,
        currency = currency,
        creatorUserId = creatorUserId,
        pendingUpdateJson = pendingUpdateJson,
        imageUrl = imageUrl,
    )

fun ExpenseSplitEntity.toDomain(): ExpenseSplit =
    ExpenseSplit(
        id = id,
        expenseId = expenseId,
        participantId = participantId,
        amount = amount,
        isPaid = isPaid != 0L,
    )

fun TripEventEntity.toDomain(json: Json): Event =
    Event(
        id = id,
        remoteId = remoteId,
        tripId = tripId,
        dayIndex = dayIndex.toInt(),
        time = time,
        title = title,
        subtitle = subtitle,
        description = description,
        duration = duration,
        cost = cost ?: 0.0,
        actualCost = actualCost ?: 0.0,
        status = status ?: "NONE",
        category = category ?: "OTHER",
        latitude = latitude ?: 0.0,
        longitude = longitude ?: 0.0,
        address = address,
        links = parseJsonList<EventLinkDto>(json, linksJson).map { EventLink(it.title, it.url) },
        comments =
            parseJsonList<EventCommentDto>(json, commentsJson).map {
                EventComment(it.userId, it.userName, it.text, it.timestamp)
            },
        files =
            parseJsonList<EventFileDto>(json, filesJson).map {
                EventFile(
                    it.name,
                    it.url,
                    it.type,
                )
            },
        participantIds = parseJsonList(json, participantIdsJson),
    )

fun TripChecklistEntity.toDomain(json: Json): ChecklistItem =
    ChecklistItem(
        id = id,
        tripId = tripId,
        title = title,
        isGroup = isGroup != 0L,
        ownerUserId = ownerUserId,
        completedBy = parseJsonList(json, completedByJson),
    )

fun UserDto.toPendingUser(): PendingUser =
    PendingUser(
        id = id,
        name = name,
        email = email,
    )

private inline fun <reified T> parseJsonList(
    json: Json,
    raw: String?,
): List<T> {
    if (raw.isNullOrBlank() || raw == "null") return emptyList()
    return try {
        json.decodeFromString(raw)
    } catch (_: Exception) {
        emptyList()
    }
}
