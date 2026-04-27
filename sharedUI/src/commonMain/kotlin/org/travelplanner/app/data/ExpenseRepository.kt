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
import org.travelplanner.app.core.V2CreateExpenseRequest
import org.travelplanner.app.core.V2ExpenseSplitRequest
import org.travelplanner.app.core.V2ExpensePendingUpdateResponse
import org.travelplanner.app.core.V2MergeExpenseRequest
import org.travelplanner.app.core.V2UpdateExpenseRequest
import org.travelplanner.app.core.toMoneyString
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.Expense
import org.travelplanner.app.domain.toDomain

class ExpenseRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
    private val participantRepo: ParticipantRepository,
    private val json: Json,
) {
    private val queries = db.expensesQueries

    val CATEGORY_PAYMENT = "PAYMENT"

    private fun getExpensesEntityFlow(tripId: String): Flow<List<TripExpenseEntity>> =
        queries.getExpensesForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    private fun getSplitsEntityFlow(tripId: String): Flow<List<ExpenseSplitEntity>> =
        queries.getSplitsForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    private fun getExpenseByIdEntity(id: Long): Flow<TripExpenseEntity?> =
        queries.getExpenseById(id).asFlow().mapToOneOrNull(Dispatchers.IO)

    fun getExpensesFlow(tripId: String): Flow<List<Expense>> =
        getExpensesEntityFlow(tripId).map { list -> list.map { it.toDomain() } }

    fun getSplitsFlow(tripId: String): Flow<List<org.travelplanner.app.domain.ExpenseSplit>> =
        getSplitsEntityFlow(tripId).map { list -> list.map { it.toDomain() } }

    fun getExpenseById(id: Long): Flow<Expense?> = getExpenseByIdEntity(id).map { it?.toDomain() }

    fun getExpenseSplitsFlow(expenseId: Long): Flow<List<org.travelplanner.app.domain.ExpenseSplit>> =
        getExpenseSplitsEntityFlow(expenseId).map { list -> list.map { it.toDomain() } }

    fun deleteExpense(id: Long) {
        queries.deleteExpense(id)
    }

    fun upsertExpenseFromResponse(
        tripId: String,
        response: ExpenseResponse,
        imageUrlOverride: String? = null,
    ) {
        db.transaction {
            val payerParticipant = participantRepo.getOrCreateParticipantLocal(tripId, response.payerUserId)
            val existingLocalId = queries.getExpenseIdByRemoteId(response.id).executeAsOneOrNull()

            val tripCurrency = db.tripsQueries.getTripById(tripId).executeAsOneOrNull()?.currency ?: "USD"

            val resolvedImageUrl = imageUrlOverride
                ?: db.attachmentsQueries
                    .getAttachmentsForExpense(response.id)
                    .executeAsList()
                    .firstOrNull()
                    ?.s3Key
                ?: queries.getExpenseByRemoteId(response.id).executeAsOneOrNull()?.imageUrl

            val pendingUpdateJson = response.pendingUpdate?.let {
                json.encodeToString(V2ExpensePendingUpdateResponse.serializer(), it)
            }

            val finalLocalId: Long
            if (existingLocalId != null) {
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
                    id = existingLocalId,
                )
                finalLocalId = existingLocalId
            } else {
                queries.insertOrReplaceExpense(
                    remoteId = response.id,
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
                finalLocalId = queries.getExpenseIdByRemoteId(response.id).executeAsOne()
            }

            queries.deleteSplitsForExpense(finalLocalId)
            response.splits.forEach { splitResponse ->
                val participant = participantRepo.getOrCreateParticipantLocal(tripId, splitResponse.participantUserId)
                queries.insertSplit(
                    expenseId = finalLocalId,
                    participantId = participant.userId,
                    amount = splitResponse.amountInExpenseCurrency,
                    isPaid = 0,
                )
            }

            val allExpenses = queries.getExpensesForTrip(tripId).executeAsList()
            val total = allExpenses
                .filter { it.category != "PAYMENT" }
                .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
            queries.updateTripSpentAmount(total.toMoneyString(), tripId)
        }
    }

    suspend fun resolveConflictOnline(tripId: String, expenseRemoteId: String, accept: Boolean) {
        if (!BackendFeatureFlags.EXPENSE_CONFLICT_ENABLED) return
        val response = api.resolveConflict(tripId, expenseRemoteId, accept) ?: return
        upsertExpenseFromResponse(tripId, response)
    }

    suspend fun mergeConflictOnline(
        tripId: String,
        expenseRemoteId: String,
        merged: V2MergeExpenseRequest,
    ) {
        if (!BackendFeatureFlags.EXPENSE_CONFLICT_ENABLED) return
        val response = api.resolveConflictMerge(tripId, expenseRemoteId, merged) ?: return
        upsertExpenseFromResponse(tripId, response)
    }

    suspend fun revertConflictOnline(tripId: String, expenseRemoteId: String) {
        if (!BackendFeatureFlags.EXPENSE_CONFLICT_ENABLED) return
        val response = api.resolveConflictRevert(tripId, expenseRemoteId) ?: return
        upsertExpenseFromResponse(tripId, response)
    }

    suspend fun addExpenseOnline(
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

        val tripCurrency = db.tripsQueries.getTripById(tripId).executeAsOneOrNull()?.currency ?: "USD"

        val globalSplits = splits.map { (localId, splitAmount) ->
            val participant = queries.getParticipantById(localId).executeAsOne()
            V2ExpenseSplitRequest(participantUserId = participant.userId, value = splitAmount.toMoneyString())
        }

        val request = V2CreateExpenseRequest(
            title = title,
            amount = amount.toMoneyString(),
            currency = tripCurrency,
            category = category,
            payerUserId = globalPayerId,
            expenseDate = run {
                val now = kotlin.time.Clock.System.now()
                kotlinx.datetime.Instant.fromEpochMilliseconds(now.toEpochMilliseconds()).toString().substringBefore('T')
            },
            splits = globalSplits,
        )

        val createdResponse = api.createExpense(tripId, request)

        val uploadedS3Key = uploadExpenseReceipt(tripId, createdResponse.id, photoBytes)

        upsertExpenseFromResponse(tripId, createdResponse, imageUrlOverride = uploadedS3Key)
    }

    private suspend fun uploadExpenseReceipt(
        tripId: String,
        expenseRemoteId: String,
        photoBytes: ByteArray?,
    ): String? {
        if (photoBytes == null) return null
        return try {
            val attachment = api.uploadExpenseFile(
                tripId,
                expenseRemoteId,
                photoBytes,
                "expense_photo.jpg",
                "image/jpeg",
            )
            db.attachmentsQueries.insertOrReplaceAttachment(
                attachment.id,
                attachment.tripId,
                attachment.expenseId,
                attachment.pointId,
                attachment.uploadedBy,
                attachment.fileName,
                attachment.fileSize,
                attachment.mimeType,
                attachment.s3Key,
                attachment.createdAt,
            )
            attachment.s3Key
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateExpenseOnline(
        tripId: String,
        expenseLocalId: Long,
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
        val remoteId = existingExpense.remoteId ?: return

        val payerParticipant = queries.getParticipantById(payerLocalId).executeAsOne()
        val globalPayerId = payerParticipant.userId

        val globalSplits = splits.map { (localId, splitAmount) ->
            val participant = queries.getParticipantById(localId).executeAsOne()
            V2ExpenseSplitRequest(participantUserId = participant.userId, value = splitAmount.toMoneyString())
        }

        val request = V2UpdateExpenseRequest(
            title = title,
            amount = amount.toMoneyString(),
            category = category,
            payerUserId = globalPayerId,
            expenseDate = existingExpense.date,
            splits = globalSplits,
            expectedVersion = existingExpense.version,
        )

        val updatedResponse = api.updateExpense(tripId, remoteId, request)

        val uploadedS3Key = uploadExpenseReceipt(tripId, remoteId, photoBytes)

        upsertExpenseFromResponse(tripId, updatedResponse, imageUrlOverride = uploadedS3Key)
    }

    fun deleteExpenseByRemoteIdLocal(remoteId: String, tripId: String) {
        queries.deleteExpenseByRemoteId(remoteId)
        val allExpenses = queries.getExpensesForTrip(tripId).executeAsList()
        val total = allExpenses.filter { it.category != "PAYMENT" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
        queries.updateTripSpentAmount(total.toMoneyString(), tripId)
    }

    suspend fun deleteExpenseOnline(remoteId: String, tripId: String) {
        api.deleteExpense(tripId, remoteId)
        queries.deleteExpenseByRemoteId(remoteId)
        // Recompute spent amount
        val allExpenses = queries.getExpensesForTrip(tripId).executeAsList()
        val total = allExpenses.filter { it.category != "PAYMENT" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
        queries.updateTripSpentAmount(total.toMoneyString(), tripId)
    }

    suspend fun settleDebt(
        tripId: String,
        debtorId: Long,
        creditorId: Long,
        amount: Double,
    ) {
        val creditor = queries.getParticipantById(creditorId).executeAsOne()

        addExpenseOnline(
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
                    if (local.remoteId != null && local.remoteId !in remoteIds) {
                        queries.deleteExpenseByRemoteId(local.remoteId)
                    }
                }

                remoteExpenses.forEach { response ->
                    upsertExpenseFromResponse(tripId, response)
                }

                val allExpenses = queries.getExpensesForTrip(tripId).executeAsList()
                val total = allExpenses.filter { it.category != "PAYMENT" }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                queries.updateTripSpentAmount(total.toMoneyString(), tripId)
            }
        } catch (e: Exception) {
            println("Expense sync failed: ${e.message}")
            throw e
        }
    }

    private fun getExpenseSplitsEntityFlow(expenseId: Long): Flow<List<ExpenseSplitEntity>> =
        queries.getSplitsForExpense(expenseId).asFlow().mapToList(Dispatchers.IO)

    fun getExpenseHistory(expenseId: Long): Flow<List<ExpenseHistoryEntity>> =
        queries.getHistoryForExpense(expenseId).asFlow().mapToList(Dispatchers.IO)
}
