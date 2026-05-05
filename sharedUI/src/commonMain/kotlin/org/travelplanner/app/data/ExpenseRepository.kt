package org.travelplanner.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.travelplanner.app.ExpenseHistoryEntity
import org.travelplanner.app.ExpenseSplitEntity
import org.travelplanner.app.TripExpenseEntity
import org.travelplanner.app.core.BackendFeatureFlags
import org.travelplanner.app.core.ExpenseResponse
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.V2CreateExpenseRequest
import org.travelplanner.app.core.V2ExpensePendingUpdateResponse
import org.travelplanner.app.core.V2ExpenseSplitRequest
import org.travelplanner.app.core.V2MergeExpenseRequest
import org.travelplanner.app.core.V2UpdateExpenseRequest
import org.travelplanner.app.core.toMoneyString
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.Expense
import org.travelplanner.app.domain.toDomain
import kotlin.time.Clock

class ExpenseRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
    private val participantRepo: ParticipantRepository,
    private val outbox: OutboxRepository,
    private val attachmentStorage: OutboxAttachmentStorage,
    private val userSession: UserSession,
    private val syncTrigger: SyncTrigger,
    private val json: Json,
) {
    private val queries = db.expensesQueries

    val CATEGORY_PAYMENT = "PAYMENT"

    private fun getExpensesEntityFlow(tripId: String): Flow<List<TripExpenseEntity>> =
        queries.getExpensesForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    private fun getSplitsEntityFlow(tripId: String): Flow<List<ExpenseSplitEntity>> =
        queries.getSplitsForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    private fun getExpenseByIdEntity(id: String): Flow<TripExpenseEntity?> =
        queries.getExpenseById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    fun getExpensesFlow(tripId: String): Flow<List<Expense>> = getExpensesEntityFlow(tripId).map { list -> list.map { it.toDomain() } }

    fun getSplitsFlow(tripId: String): Flow<List<org.travelplanner.app.domain.ExpenseSplit>> =
        getSplitsEntityFlow(tripId).map { list -> list.map { it.toDomain() } }

    fun getExpenseById(id: String): Flow<Expense?> = getExpenseByIdEntity(id).map { it?.toDomain() }

    fun getExpenseSplitsFlow(expenseId: String): Flow<List<org.travelplanner.app.domain.ExpenseSplit>> =
        getExpenseSplitsEntityFlow(expenseId).map { list -> list.map { it.toDomain() } }

    fun upsertExpenseFromResponse(
        tripId: String,
        response: ExpenseResponse,
        imageUrlOverride: String? = null,
    ) {
        db.transaction {
            val payerParticipant =
                participantRepo.getOrCreateParticipantLocal(tripId, response.payerUserId)

            val tripCurrency =
                db.tripsQueries
                    .getTripById(tripId)
                    .executeAsOneOrNull()
                    ?.currency ?: "USD"

            val existing = queries.getExpenseById(response.id).executeAsOneOrNull()

            val resolvedImageUrl =
                imageUrlOverride
                    ?: db.attachmentsQueries
                        .getAttachmentsForExpense(response.id)
                        .executeAsList()
                        .firstOrNull()
                        ?.s3Key
                    ?: existing?.imageUrl

            val pendingUpdateJson =
                response.pendingUpdate?.let {
                    json.encodeToString(V2ExpensePendingUpdateResponse.serializer(), it)
                }

            if (existing != null) {
                queries.updateExpenseDetails(
                    title = response.title,
                    amount = response.amount,
                    category = response.category,
                    payerName = payerParticipant.name,
                    date = response.expenseDate,
                    splitDescription = "Всего",
                    currency = tripCurrency,
                    creatorUserId = response.createdBy,
                    pendingUpdateJson = pendingUpdateJson,
                    imageUrl = resolvedImageUrl,
                    version = response.version,
                    splitType = response.splitType,
                    id = response.id,
                )
            } else {
                queries.insertOrReplaceExpense(
                    id = response.id,
                    tripId = tripId,
                    title = response.title,
                    amount = response.amount,
                    category = response.category,
                    payerName = payerParticipant.name,
                    date = response.expenseDate,
                    splitDescription = "Всего",
                    currency = tripCurrency,
                    creatorUserId = response.createdBy,
                    pendingUpdateJson = pendingUpdateJson,
                    imageUrl = resolvedImageUrl,
                    version = response.version,
                    splitType = response.splitType,
                )
            }

            queries.deleteSplitsForExpense(response.id)
            response.splits.forEach { splitResponse ->
                val participant =
                    participantRepo.getOrCreateParticipantLocal(
                        tripId,
                        splitResponse.participantUserId,
                    )
                queries.insertSplit(
                    expenseId = response.id,
                    participantId = participant.userId,
                    amount = splitResponse.amountInExpenseCurrency,
                    isPaid = 0,
                )
            }

            recalculateTripSpent(tripId)
        }
    }

    private fun recalculateTripSpent(tripId: String) {
        val allExpenses = queries.getExpensesForTrip(tripId).executeAsList()
        val total =
            allExpenses
                .filter { it.category != "PAYMENT" }
                .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
        queries.updateTripSpentAmount(total.toMoneyString(), tripId)
    }

    suspend fun resolveConflict(
        tripId: String,
        expenseRemoteId: String,
        accept: Boolean,
    ) {
        if (!BackendFeatureFlags.EXPENSE_CONFLICT_ENABLED) return
        val response = api.resolveConflict(tripId, expenseRemoteId, accept) ?: return
        upsertExpenseFromResponse(tripId, response)
        clearConflictOutboxEntries(expenseRemoteId)
    }

    suspend fun mergeConflict(
        tripId: String,
        expenseRemoteId: String,
        merged: V2MergeExpenseRequest,
    ) {
        if (!BackendFeatureFlags.EXPENSE_CONFLICT_ENABLED) return
        val response = api.resolveConflictMerge(tripId, expenseRemoteId, merged) ?: return
        upsertExpenseFromResponse(tripId, response)
        clearConflictOutboxEntries(expenseRemoteId)
    }

    suspend fun revertConflict(
        tripId: String,
        expenseRemoteId: String,
    ) {
        if (!BackendFeatureFlags.EXPENSE_CONFLICT_ENABLED) return
        val response = api.resolveConflictRevert(tripId, expenseRemoteId) ?: return
        upsertExpenseFromResponse(tripId, response)
        clearConflictOutboxEntries(expenseRemoteId)
    }

    private fun clearConflictOutboxEntries(expenseRemoteId: String) {
        val entries =
            outbox.getEntriesForEntityByState(
                entityType = OutboxEntityType.EXPENSE,
                entityId = expenseRemoteId,
                state = OutboxState.CONFLICT,
            )
        entries.forEach { outbox.markSuccess(it.id) }
    }

    suspend fun addExpense(
        tripId: String,
        title: String,
        amount: Double,
        category: String,
        payerLocalId: Long,
        splits: Map<Long, Double>,
        photoBytes: ByteArray? = null,
    ) {
        val payerParticipant = queries.getParticipantById(payerLocalId).executeAsOne()
        val globalPayerId = payerParticipant.userId

        val tripCurrency =
            db.tripsQueries
                .getTripById(tripId)
                .executeAsOneOrNull()
                ?.currency ?: "USD"

        val splitParticipants =
            splits.entries.map { (localId, splitAmount) ->
                val participant = queries.getParticipantById(localId).executeAsOne()
                participant.userId to splitAmount
            }
        val globalSplits =
            splitParticipants.map { (userId, splitAmount) ->
                V2ExpenseSplitRequest(
                    participantUserId = userId,
                    value = splitAmount.toMoneyString(),
                )
            }

        val expenseId = outbox.newMutationId()
        val mutationId = outbox.newMutationId()
        val expenseDate = isoDateToday()
        val request =
            V2CreateExpenseRequest(
                id = expenseId,
                clientMutationId = mutationId,
                title = title,
                amount = amount.toMoneyString(),
                currency = tripCurrency,
                category = category,
                payerUserId = globalPayerId,
                expenseDate = expenseDate,
                splits = globalSplits,
            )

        val photoLocalPath: String? =
            photoBytes
                ?.let { bytes ->
                    val attachmentId = outbox.newMutationId()
                    runCatching { attachmentStorage.write(attachmentId, bytes) }
                        .getOrNull()
                        ?.let { path ->
                            attachmentId to path
                        }
                }?.let { (attachmentId, path) -> "$attachmentId|$path" }

        db.transaction {
            queries.insertOrReplaceExpense(
                id = expenseId,
                tripId = tripId,
                title = title,
                amount = amount.toMoneyString(),
                category = category,
                payerName = payerParticipant.name,
                date = expenseDate,
                splitDescription = "Всего",
                currency = tripCurrency,
                creatorUserId =
                    userSession.currentUser.value
                        ?.id
                        .orEmpty(),
                pendingUpdateJson = null,
                imageUrl = photoLocalPath?.let { "pending://${it.substringAfter('|')}" },
                version = 0L,
                splitType = "EQUAL",
            )
            splitParticipants.forEach { (participantUserId, splitAmount) ->
                queries.insertSplit(
                    expenseId = expenseId,
                    participantId = participantUserId,
                    amount = splitAmount.toMoneyString(),
                    isPaid = 0,
                )
            }
            recalculateTripSpent(tripId)

            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.EXPENSE,
                entityId = expenseId,
                operation = OutboxOperation.CREATE,
                payloadJson = json.encodeToString(V2CreateExpenseRequest.serializer(), request),
                baseVersion = null,
                existingMutationId = mutationId,
            )

            if (photoLocalPath != null) {
                val attachmentId = photoLocalPath.substringBefore('|')
                val absolutePath = photoLocalPath.substringAfter('|')
                enqueueAttachmentOutbox(
                    tripId = tripId,
                    attachmentId = attachmentId,
                    expenseId = expenseId,
                    pointId = null,
                    fileName = "expense_photo.jpg",
                    mimeType = "image/jpeg",
                    fileSize = photoBytes.size.toLong(),
                    absolutePath = absolutePath,
                )
            }
        }

        syncTrigger.requestSync()
    }

    private fun enqueueAttachmentOutbox(
        tripId: String,
        attachmentId: String,
        expenseId: String?,
        pointId: String?,
        fileName: String,
        mimeType: String,
        fileSize: Long,
        absolutePath: String,
    ) {
        val now =
            kotlin.time.Instant
                .fromEpochMilliseconds(
                    kotlin.time.Clock.System
                        .now()
                        .toEpochMilliseconds(),
                ).toString()
        db.attachmentsQueries.insertOrReplaceAttachment(
            attachmentId,
            tripId,
            expenseId,
            pointId,
            userSession.currentUser.value
                ?.id
                .orEmpty(),
            fileName,
            fileSize,
            mimeType,
            "pending://$absolutePath",
            now,
        )

        val payload =
            AttachmentOutboxPayload(
                attachmentId = attachmentId,
                tripId = tripId,
                expenseId = expenseId,
                pointId = pointId,
                fileName = fileName,
                mimeType = mimeType,
                fileSizeBytes = fileSize,
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
    }

    suspend fun updateExpense(
        tripId: String,
        expenseLocalId: String,
        title: String,
        amount: Double,
        category: String,
        payerLocalId: Long,
        splits: Map<Long, Double>,
        existingImageUrl: String? = null,
        photoBytes: ByteArray? = null,
        force: Boolean = false,
    ) {
        val existingExpense = queries.getExpenseById(expenseLocalId).executeAsOne()
        val payerParticipant = queries.getParticipantById(payerLocalId).executeAsOne()
        val globalPayerId = payerParticipant.userId

        val splitParticipants =
            splits.entries.map { (localId, splitAmount) ->
                val participant = queries.getParticipantById(localId).executeAsOne()
                participant.userId to splitAmount
            }
        val globalSplits =
            splitParticipants.map { (userId, splitAmount) ->
                V2ExpenseSplitRequest(
                    participantUserId = userId,
                    value = splitAmount.toMoneyString(),
                )
            }

        val mutationId = outbox.newMutationId()
        val request =
            V2UpdateExpenseRequest(
                clientMutationId = mutationId,
                title = title,
                amount = amount.toMoneyString(),
                category = category,
                payerUserId = globalPayerId,
                expenseDate = existingExpense.date,
                splits = globalSplits,
                expectedVersion = existingExpense.version,
            )

        val photoLocalPath: String? =
            photoBytes?.let { bytes ->
                val attachmentId = outbox.newMutationId()
                runCatching { attachmentStorage.write(attachmentId, bytes) }
                    .getOrNull()
                    ?.let { path ->
                        "$attachmentId|$path"
                    }
            }

        db.transaction {
            queries.updateExpenseDetails(
                title = title,
                amount = amount.toMoneyString(),
                category = category,
                payerName = payerParticipant.name,
                date = existingExpense.date,
                splitDescription = existingExpense.splitDescription,
                currency = existingExpense.currency,
                creatorUserId = existingExpense.creatorUserId,
                pendingUpdateJson = existingExpense.pendingUpdateJson,
                imageUrl =
                    photoLocalPath?.let { "pending://${it.substringAfter('|')}" }
                        ?: existingImageUrl
                        ?: existingExpense.imageUrl,
                version = existingExpense.version,
                splitType = existingExpense.splitType,
                id = expenseLocalId,
            )
            queries.deleteSplitsForExpense(expenseLocalId)
            splitParticipants.forEach { (participantUserId, splitAmount) ->
                queries.insertSplit(
                    expenseId = expenseLocalId,
                    participantId = participantUserId,
                    amount = splitAmount.toMoneyString(),
                    isPaid = 0,
                )
            }
            recalculateTripSpent(tripId)

            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.EXPENSE,
                entityId = expenseLocalId,
                operation = OutboxOperation.UPDATE,
                payloadJson = json.encodeToString(V2UpdateExpenseRequest.serializer(), request),
                baseVersion = existingExpense.version,
                existingMutationId = mutationId,
            )

            if (photoLocalPath != null) {
                val attachmentId = photoLocalPath.substringBefore('|')
                val absolutePath = photoLocalPath.substringAfter('|')
                enqueueAttachmentOutbox(
                    tripId = tripId,
                    attachmentId = attachmentId,
                    expenseId = expenseLocalId,
                    pointId = null,
                    fileName = "expense_photo.jpg",
                    mimeType = "image/jpeg",
                    fileSize = photoBytes.size.toLong(),
                    absolutePath = absolutePath,
                )
            }
        }

        syncTrigger.requestSync()
    }

    fun deleteExpenseLocal(
        id: String,
        tripId: String,
    ) {
        queries.deleteExpense(id)
        recalculateTripSpent(tripId)
    }

    fun deleteExpense(
        id: String,
        tripId: String,
    ) {
        val existing = queries.getExpenseById(id).executeAsOneOrNull() ?: return
        val mutationId = outbox.newMutationId()
        val payloadJson =
            json.encodeToString(
                V2UpdateExpenseRequest.serializer(),
                V2UpdateExpenseRequest(
                    clientMutationId = mutationId,
                    expectedVersion = existing.version,
                ),
            )

        db.transaction {
            queries.deleteExpense(id)
            recalculateTripSpent(tripId)

            outbox.enqueue(
                tripId = tripId,
                entityType = OutboxEntityType.EXPENSE,
                entityId = id,
                operation = OutboxOperation.DELETE,
                payloadJson = payloadJson,
                baseVersion = existing.version,
                existingMutationId = mutationId,
            )
        }
        syncTrigger.requestSync()
    }

    suspend fun settleDebt(
        tripId: String,
        debtorId: Long,
        creditorId: Long,
        amount: Double,
    ) {
        val creditor = queries.getParticipantById(creditorId).executeAsOne()
        addExpense(
            tripId = tripId,
            title = "Платёж для ${creditor.name}",
            amount = amount,
            category = "PAYMENT",
            payerLocalId = debtorId,
            splits = mapOf(creditorId to amount),
        )
    }

    suspend fun syncExpenses(tripId: String) {
        try {
            val remoteExpenses = api.getExpenses(tripId)
            val remoteIds = remoteExpenses.map { it.id }.toSet()

            db.transaction {
                val localExpenses = queries.getExpensesForTrip(tripId).executeAsList()

                localExpenses.forEach { local ->
                    if (local.id !in remoteIds) {
                        queries.deleteExpense(local.id)
                    }
                }

                remoteExpenses.forEach { response ->
                    upsertExpenseFromResponse(tripId, response)
                }

                recalculateTripSpent(tripId)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Expense sync failed: ${e.message}")
        }
    }

    private fun getExpenseSplitsEntityFlow(expenseId: String): Flow<List<ExpenseSplitEntity>> =
        queries.getSplitsForExpense(expenseId).asFlow().mapToList(Dispatchers.IO)

    fun getExpenseHistory(expenseId: String): Flow<List<ExpenseHistoryEntity>> =
        queries.getHistoryForExpense(expenseId).asFlow().mapToList(Dispatchers.IO)

    private fun isoDateToday(): String {
        val now = Clock.System.now().toEpochMilliseconds()
        return kotlin.time.Instant
            .fromEpochMilliseconds(now)
            .toString()
            .substringBefore('T')
    }
}
