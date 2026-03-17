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
import org.travelplanner.app.core.CreateExpenseRequest
import org.travelplanner.app.core.ExpenseDto
import org.travelplanner.app.core.SplitDto
import org.travelplanner.app.core.TripApiService
import org.travelplanner.app.db.MyDatabase
import org.travelplanner.app.domain.Expense
import org.travelplanner.app.domain.toDomain
import org.travelplanner.app.features.tripDetails.history.ConflictException
import kotlin.time.Clock.System

class ExpenseRepository(
    private val db: MyDatabase,
    private val api: TripApiService,
    private val participantRepo: ParticipantRepository,
    private val json: Json,
) {
    private val queries = db.expensesQueries

    fun resolveUrl(path: String?): String? = api.resolveUrl(path)

    val CATEGORY_PAYMENT = "PAYMENT"

    private fun getExpensesEntityFlow(tripId: Long): Flow<List<TripExpenseEntity>> =
        queries.getExpensesForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    private fun getSplitsEntityFlow(tripId: Long): Flow<List<ExpenseSplitEntity>> =
        queries.getSplitsForTrip(tripId).asFlow().mapToList(Dispatchers.IO)

    private fun getExpenseByIdEntity(id: Long): Flow<TripExpenseEntity?> =
        queries
            .getExpenseById(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    fun getExpensesFlow(tripId: Long): Flow<List<Expense>> = getExpensesEntityFlow(tripId).map { list -> list.map { it.toDomain() } }

    fun getSplitsFlow(tripId: Long): Flow<List<org.travelplanner.app.domain.ExpenseSplit>> =
        getSplitsEntityFlow(tripId).map { list -> list.map { it.toDomain() } }

    fun getExpenseById(id: Long): Flow<Expense?> = getExpenseByIdEntity(id).map { it?.toDomain() }

    fun getExpenseSplitsFlow(expenseId: Long): Flow<List<org.travelplanner.app.domain.ExpenseSplit>> =
        getExpenseSplitsEntityFlow(expenseId).map { list -> list.map { it.toDomain() } }

    fun deleteExpense(id: Long) {
        queries.deleteExpense(id)
    }

    fun upsertExpenseFromDto(
        tripId: Long,
        dto: ExpenseDto,
    ) {
        db.transaction {
            val payerParticipant =
                participantRepo.getOrCreateParticipantLocal(tripId, dto.payerUserId)
            val existingLocalId = queries.getExpenseIdByRemoteId(dto.id).executeAsOneOrNull()

            val pendingJson = dto.pendingUpdate?.let { json.encodeToString(it) }

            val tripCurrency =
                db.tripsQueries
                    .getTripById(tripId)
                    .executeAsOneOrNull()
                    ?.currency ?: "¥"

            val finalLocalId: Long
            if (existingLocalId != null) {
                queries.updateExpenseDetails(
                    title = dto.title,
                    amount = dto.amount,
                    category = dto.category,
                    payerName = payerParticipant.name,
                    date = dto.date,
                    splitDescription = "Итого",
                    currency = tripCurrency,
                    creatorUserId = dto.creatorUserId,
                    pendingUpdateJson = pendingJson,
                    id = existingLocalId,
                    imageUrl = dto.imageUrl,
                )
                finalLocalId = existingLocalId
            } else {
                queries.insertOrReplaceExpense(
                    remoteId = dto.id,
                    tripId = tripId,
                    title = dto.title,
                    amount = dto.amount,
                    category = dto.category,
                    payerName = payerParticipant.name,
                    date = dto.date,
                    splitDescription = "Итого",
                    currency = tripCurrency,
                    creatorUserId = dto.creatorUserId,
                    pendingUpdateJson = pendingJson,
                    imageUrl = dto.imageUrl,
                )
                finalLocalId = queries.getExpenseIdByRemoteId(dto.id).executeAsOne()
            }

            queries.deleteSplitsForExpense(finalLocalId)
            dto.splits.forEach { splitDto ->
                val participant =
                    participantRepo.getOrCreateParticipantLocal(tripId, splitDto.userId)
                queries.insertSplit(
                    expenseId = finalLocalId,
                    participantId = participant.id,
                    amount = splitDto.amount,
                    isPaid = 0,
                )
            }

            queries.updateTripSpentAmount(tripId)
        }
    }

    suspend fun resolveConflictOnline(
        tripId: Long,
        expenseRemoteId: String,
        accept: Boolean,
    ) {
        val updatedDto = api.resolveConflict(tripId, expenseRemoteId, accept)
        upsertExpenseFromDto(tripId, updatedDto)
    }

    suspend fun addExpenseOnline(
        tripId: Long,
        title: String,
        amount: Double,
        category: String,
        payerLocalId: Long,
        splits: Map<Long, Double>,
        photoBytes: ByteArray? = null,
    ) {
        val uploadedImageUrl = photoBytes?.let { api.uploadPhoto(it) }

        val payerParticipant = queries.getParticipantById(payerLocalId).executeAsOne()
        val globalPayerId =
            payerParticipant.userId ?: payerParticipant.name

        val globalSplits =
            splits.map { (localId, splitAmount) ->
                val participant = queries.getParticipantById(localId).executeAsOne()
                val globalUserId = participant.userId ?: participant.name

                SplitDto(userId = globalUserId, amount = splitAmount)
            }

        val request =
            CreateExpenseRequest(
                title = title,
                amount = amount,
                category = category,
                payerUserId = globalPayerId,
                date = System.now().toEpochMilliseconds(),
                splits = globalSplits,
                imageUrl = uploadedImageUrl,
            )

        val createdDto = api.addExpense(tripId, request)
        upsertExpenseFromDto(tripId, createdDto)
    }

    suspend fun updateExpenseOnline(
        tripId: Long,
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

        val finalImageUrl =
            if (photoBytes != null) {
                api.uploadPhoto(photoBytes)
            } else {
                existingImageUrl
            }

        val payerParticipant = queries.getParticipantById(payerLocalId).executeAsOne()
        val globalPayerId = payerParticipant.userId ?: payerParticipant.name

        val globalSplits =
            splits.map { (localId, splitAmount) ->
                val participant = queries.getParticipantById(localId).executeAsOne()
                val globalUserId = participant.userId ?: participant.name
                SplitDto(userId = globalUserId, amount = splitAmount)
            }

        val request =
            CreateExpenseRequest(
                title = title,
                amount = amount,
                category = category,
                payerUserId = globalPayerId,
                date = existingExpense.date,
                splits = globalSplits,
                imageUrl = finalImageUrl,
            )

        val baseHash =
            (existingExpense.amount.toString() + existingExpense.title + existingExpense.category)
                .hashCode()
                .toString()

        try {
            val updatedDto = api.updateExpense(tripId, remoteId, request, baseHash, force)
            upsertExpenseFromDto(tripId, updatedDto)
        } catch (e: ConflictException) {
            throw e
        }
    }

    suspend fun deleteExpenseOnline(
        remoteId: String,
        tripId: Long,
    ) {
        api.deleteExpense(tripId, remoteId)
        queries.deleteExpenseByRemoteId(remoteId)
    }

    suspend fun settleDebt(
        tripId: Long,
        debtorId: Long,
        creditorId: Long,
        amount: Double,
    ) {
        val creditor = queries.getParticipantById(creditorId).executeAsOne()

        addExpenseOnline(
            tripId = tripId,
            title = "Payment to ${creditor.name}",
            amount = amount,
            category = "PAYMENT",
            payerLocalId = debtorId,
            splits = mapOf(creditorId to amount),
        )
    }

    suspend fun syncExpenses(tripId: Long) {
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

                remoteExpenses.forEach { dto ->
                    upsertExpenseFromDto(tripId, dto)
                }

                queries.updateTripSpentAmount(tripId)
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
