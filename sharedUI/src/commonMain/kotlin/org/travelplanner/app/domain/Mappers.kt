package org.travelplanner.app.domain

import kotlinx.serialization.json.Json
import org.travelplanner.app.ExpenseSplitEntity
import org.travelplanner.app.TripChecklistEntity
import org.travelplanner.app.TripEntity
import org.travelplanner.app.TripEventEntity
import org.travelplanner.app.TripExpenseEntity
import org.travelplanner.app.TripParticipantEntity
import org.travelplanner.app.core.ExpenseResponse
import org.travelplanner.app.core.ItineraryPointResponse
import org.travelplanner.app.core.ParticipantDetailResponse
import org.travelplanner.app.core.TripResponse
import org.travelplanner.app.core.JoinRequestUserResponse
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
        baseCurrency = baseCurrency,
        version = version,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun TripParticipantEntity.toDomain(): Participant =
    Participant(
        id = id,
        tripId = tripId,
        userId = userId,
        name = name,
        email = email,
        role = role,
        joinedAt = joinedAt,
        avatarUrl = avatarUrl,
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
        version = version,
        splitType = splitType,
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
                EventFile(it.name, it.url, it.type)
            },
        participantIds = parseJsonList(json, participantIdsJson),
        sortOrder = sortOrder.toInt(),
        type = type,
        startTime = startTime,
        endTime = endTime,
        version = version,
        createdBy = createdBy,
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

fun JoinRequestUserResponse.toPendingUser(): PendingUser =
    PendingUser(
        id = userId,
        name = displayName,
        email = email,
    )

// -- New response-to-domain mappers --

fun TripResponse.toDomain(): Trip =
    Trip(
        id = id,
        title = title,
        destination = destination,
        startDate = startDate,
        endDate = endDate,
        currency = baseCurrency,
        totalBudget = totalBudget,
        spentAmount = "0",
        description = description,
        status = status,
        joinCode = joinCode,
        ownerUserId = createdBy,
        imageUrl = imageUrl,
        baseCurrency = baseCurrency,
        version = version,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ExpenseResponse.toDomain(): Expense =
    Expense(
        id = 0,
        remoteId = id,
        tripId = tripId,
        title = title,
        amount = amount,
        category = category,
        payerName = payerUserId,
        date = expenseDate,
        splitDescription = splits.joinToString(", ") { "${it.participantUserId}: ${it.amountInExpenseCurrency}" },
        currency = currency,
        creatorUserId = createdBy,
        pendingUpdateJson = null,
        imageUrl = null,
        version = version,
        splitType = splitType,
    )

fun ItineraryPointResponse.toDomain(): Event =
    Event(
        id = 0,
        remoteId = id,
        tripId = tripId,
        dayIndex = dayIndex,
        time = startTime ?: "",
        title = title,
        subtitle = subtitle.orEmpty(),
        description = description,
        duration = duration,
        cost = cost ?: 0.0,
        actualCost = actualCost ?: 0.0,
        status = status,
        category = category ?: type.orEmpty(),
        latitude = latitude ?: 0.0,
        longitude = longitude ?: 0.0,
        address = address,
        links = emptyList(),
        comments = emptyList(),
        files = emptyList(),
        participantIds = participantIds,
        sortOrder = sortOrder,
        type = type,
        startTime = startTime,
        endTime = endTime,
        version = version,
        createdBy = createdBy,
    )

fun ParticipantDetailResponse.toDomain(tripId: String): Participant =
    Participant(
        id = 0,
        tripId = tripId,
        userId = userId,
        name = displayName,
        email = email,
        role = role,
        avatarUrl = avatarUrl,
        joinedAt = joinedAt,
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
