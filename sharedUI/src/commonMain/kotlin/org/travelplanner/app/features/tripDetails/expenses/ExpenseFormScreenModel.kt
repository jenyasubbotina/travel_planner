package org.travelplanner.app.features.tripDetails.expenses

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.travelplanner.app.core.AnotherPendingUpdateException
import org.travelplanner.app.core.BaseScreenModel
import org.travelplanner.app.core.PendingUpdateStoredException
import org.travelplanner.app.core.TripUtils.formatDate
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.toEpochMillis
import org.travelplanner.app.core.toMoneyDouble
import org.travelplanner.app.data.ExpenseRepository
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import kotlin.math.abs

class ExpenseFormScreenModel(
    private val tripId: String,
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository,
    private val userSession: UserSession,
    private val tripRepository: TripRepository,
) : BaseScreenModel<AddExpenseState, ExpenseFormIntent, ExpenseFormEffect>(AddExpenseState()) {
    private var editingExpenseId: Long? = null

    override fun handleIntent(intent: ExpenseFormIntent) {
        when (intent) {
            is ExpenseFormIntent.Initialize -> {
                initialize(intent.expenseId)
            }

            is ExpenseFormIntent.AmountChanged -> {
                onAmountChange(intent.value)
            }

            is ExpenseFormIntent.CategoryChanged -> {
                updateState { copy(category = intent.category) }
            }

            is ExpenseFormIntent.DescriptionChanged -> {
                updateState { copy(description = intent.description) }
            }

            is ExpenseFormIntent.PayerChanged -> {
                updateState { copy(payerId = intent.id) }
            }

            is ExpenseFormIntent.DateChanged -> {
                if (intent.date != null) updateState { copy(date = intent.date) }
            }

            is ExpenseFormIntent.SplitMethodChanged -> {
                onSplitMethodChange(intent.method)
            }

            is ExpenseFormIntent.ManualAmountChanged -> {
                onManualAmountChange(
                    intent.participantId,
                    intent.value,
                )
            }

            is ExpenseFormIntent.ParticipantToggled -> {
                onParticipantToggle(intent.id)
            }

            is ExpenseFormIntent.PhotoSelected -> {
                updateState { copy(photoBytes = intent.bytes) }
            }

            is ExpenseFormIntent.Save -> {
                save()
            }
        }
    }

    private fun initialize(expenseId: Long?) {
        screenModelScope.launch {
            val participants = participantRepository.getParticipantsFlow(tripId).first()
            val currentUser = userSession.currentUser.value
            val trip = tripRepository.getTripById(tripId).firstOrNull()
            val currency = trip?.currency ?: "¥"

            if (expenseId == null) {
                val myParticipantId =
                    if (currentUser != null) {
                        participants.find { it.userId == currentUser.id }?.id
                    } else {
                        null
                    }
                val defaultPayerId = myParticipantId ?: participants.firstOrNull()?.id

                updateState {
                    AddExpenseState(
                        participants =
                            participants.map {
                                ParticipantSplitState(participant = it, isSelected = true)
                            },
                        payerId = defaultPayerId,
                        currency = currency,
                    )
                }
                editingExpenseId = null
                recalculate()
            } else {
                val expense =
                    expenseRepository.getExpenseById(expenseId).firstOrNull() ?: return@launch
                val splits = expenseRepository.getExpenseSplitsFlow(expenseId).first()

                val amounts = splits.map { it.amount.toMoneyDouble() }
                val isManual =
                    if (amounts.isNotEmpty()) {
                        val avg = amounts.average()
                        amounts.any { abs(it - avg) > 0.1 }
                    } else {
                        false
                    }

                updateState {
                    AddExpenseState(
                        amount = expense.amount.toMoneyDouble().toInt().toString(),
                        category = expense.category,
                        description = expense.title,
                        date = expense.date.toEpochMillis(),
                        payerId = participants.find { it.name == expense.payerName }?.id,
                        splitMethod = if (isManual) SplitMethod.MANUAL else SplitMethod.EQUAL,
                        participants =
                            participants.map { p ->
                                val existingSplit = splits.find { it.participantId == p.userId }
                                ParticipantSplitState(
                                    participant = p,
                                    isSelected = existingSplit != null,
                                    manualAmount = existingSplit?.amount?.toMoneyDouble()?.toInt()?.toString() ?: "",
                                )
                            },
                        imageUrl = expense.imageUrl,
                        currency = currency,
                    )
                }
                editingExpenseId = expenseId
                recalculate()
            }
        }
    }

    private fun onSplitMethodChange(method: SplitMethod) {
        val oldMethod = currentState.splitMethod
        updateState { copy(splitMethod = method) }

        if (oldMethod == SplitMethod.EQUAL && method == SplitMethod.MANUAL) {
            val total = currentState.amount.toDoubleOrNull() ?: 0.0
            val activeCount = currentState.participants.count { it.isSelected }
            if (activeCount > 0) {
                val share = (total / activeCount).toInt().toString()
                val updatedParts =
                    currentState.participants.map {
                        if (it.isSelected) it.copy(manualAmount = share) else it
                    }
                updateState { copy(participants = updatedParts) }
            }
        }
        recalculate()
    }

    private fun onAmountChange(valStr: String) {
        if (valStr.all { it.isDigit() || it == '.' }) {
            updateState { copy(amount = valStr) }
            recalculate()
        }
    }

    private fun onManualAmountChange(
        id: Long,
        valStr: String,
    ) {
        if (valStr.all { it.isDigit() || it == '.' }) {
            val updated =
                currentState.participants.map {
                    if (it.participant.id == id) {
                        it.copy(manualAmount = valStr, isSelected = true)
                    } else {
                        it
                    }
                }
            updateState { copy(participants = updated) }
            recalculate()
        }
    }

    private fun onParticipantToggle(id: Long) {
        val updated =
            currentState.participants.map {
                if (it.participant.id == id) it.copy(isSelected = !it.isSelected) else it
            }
        updateState { copy(participants = updated) }
        recalculate()
    }

    private fun recalculate() {
        val s = currentState
        val total = s.amount.toDoubleOrNull() ?: 0.0

        if (s.splitMethod == SplitMethod.EQUAL) {
            val activeCount = s.participants.count { it.isSelected }
            val share = if (activeCount > 0) total / activeCount else 0.0
            val updated =
                s.participants.map {
                    it.copy(calculatedAmount = if (it.isSelected) share else 0.0)
                }
            updateState { copy(participants = updated, isSplitValid = true, splitError = null) }
        } else {
            val updated =
                s.participants.map {
                    val amt = it.manualAmount.toDoubleOrNull() ?: 0.0
                    it.copy(calculatedAmount = if (it.isSelected) amt else 0.0)
                }
            val sumOfManual = updated.filter { it.isSelected }.sumOf { it.calculatedAmount }
            val diff = total - sumOfManual
            val isValid = abs(diff) < 0.01
            val errorMsg =
                when {
                    diff > 0 -> "Missing: ${diff.toInt()}"
                    diff < 0 -> "Over by: ${abs(diff).toInt()}"
                    else -> null
                }
            updateState {
                copy(
                    participants = updated,
                    isSplitValid = isValid,
                    splitError = errorMsg,
                )
            }
        }
    }

    private fun save() {
        val s = currentState
        val total = s.amount.toDoubleOrNull() ?: 0.0
        val selectedPayerId = s.payerId ?: return

        if (total <= 0.0 || s.description.isBlank() || !s.isSplitValid) return

        val splitsMap =
            s.participants
                .filter { it.isSelected }
                .associate { it.participant.id to it.calculatedAmount }

        screenModelScope.launch {
            try {
                if (editingExpenseId == null) {
                    expenseRepository.addExpenseOnline(
                        tripId = tripId,
                        title = s.description,
                        amount = total,
                        category = s.category,
                        payerLocalId = selectedPayerId,
                        splits = splitsMap,
                        photoBytes = s.photoBytes,
                    )
                } else {
                    expenseRepository.updateExpenseOnline(
                        tripId = tripId,
                        expenseLocalId = editingExpenseId!!,
                        title = s.description,
                        amount = total,
                        category = s.category,
                        payerLocalId = selectedPayerId,
                        splits = splitsMap,
                        existingImageUrl = s.imageUrl,
                        photoBytes = s.photoBytes,
                    )
                }
                sendEffect(ExpenseFormEffect.SaveSuccess)
            } catch (e: PendingUpdateStoredException) {
                // Approval-required model: a non-creator's edit was queued. Treat as a successful
                // submission from the user's POV — the next sync will surface the pending row.
                sendEffect(ExpenseFormEffect.SaveQueuedForApproval)
            } catch (e: AnotherPendingUpdateException) {
                // Another participant already has a proposal in flight; surface so UI can warn
                // and (optionally) keep the form open so the user knows their edit didn't land.
                sendEffect(ExpenseFormEffect.SaveBlockedAnotherPending)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getFormattedDate() = formatDate(currentState.date)
}
