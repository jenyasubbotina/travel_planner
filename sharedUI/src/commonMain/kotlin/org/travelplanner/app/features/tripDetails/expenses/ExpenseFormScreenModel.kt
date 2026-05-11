package org.travelplanner.app.features.tripDetails.expenses

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.travelplanner.app.core.AnotherPendingUpdateException
import org.travelplanner.app.core.BaseScreenModel
import org.travelplanner.app.core.PendingUpdateStoredException
import org.travelplanner.app.core.TripUtils.formatDate
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.Validation
import org.travelplanner.app.core.currencySymbol
import org.travelplanner.app.core.toEpochMillis
import org.travelplanner.app.core.toMoneyDouble
import org.travelplanner.app.core.toMoneyString
import org.travelplanner.app.data.ExpenseRepository
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.SplitInput
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.domain.Participant
import kotlin.math.abs
import kotlin.math.round

class ExpenseFormScreenModel(
    private val tripId: String,
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository,
    private val userSession: UserSession,
    private val tripRepository: TripRepository,
) : BaseScreenModel<AddExpenseState, ExpenseFormIntent, ExpenseFormEffect>(AddExpenseState()) {
    private var editingExpenseId: String? = null
    private var hasInitialized: Boolean = false

    init {
        screenModelScope.launch {
            participantRepository.getParticipantsFlow(tripId).collectLatest { latest ->
                if (hasInitialized) syncParticipants(latest)
            }
        }
    }

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
                updateState {
                    copy(
                        description = intent.description,
                        descriptionError = if (showErrors) computeDescriptionError(intent.description) else descriptionError,
                    )
                }
            }

            is ExpenseFormIntent.PayerChanged -> {
                updateState {
                    copy(
                        payerId = intent.id,
                        payerError = if (showErrors) computePayerError(intent.id) else payerError,
                    )
                }
            }

            is ExpenseFormIntent.DateChanged -> {
                if (intent.date != null) updateState { copy(date = intent.date) }
            }

            is ExpenseFormIntent.SplitMethodChanged -> {
                onSplitMethodChange(intent.method)
            }

            is ExpenseFormIntent.InputValueChanged -> {
                onInputValueChange(
                    intent.participantId,
                    intent.value,
                )
            }

            is ExpenseFormIntent.ParticipantToggled -> {
                onParticipantToggle(intent.id)
            }

            is ExpenseFormIntent.PhotoSelected -> {
                updateState {
                    copy(
                        photoBytes = intent.bytes,
                        imageUrl = if (intent.bytes == null) null else imageUrl,
                    )
                }
            }

            is ExpenseFormIntent.Save -> {
                save()
            }
        }
    }

    private fun initialize(expenseId: String?) {
        screenModelScope.launch {
            val participants = participantRepository.getParticipantsFlow(tripId).first()
            val trip = tripRepository.getTripById(tripId).firstOrNull()
            val currency = currencySymbol(trip?.currency ?: "RUB")

            if (expenseId == null) {
                updateState { bootstrapDefaults(participants, currency) }
                editingExpenseId = null
            } else {
                val expense =
                    expenseRepository.getExpenseById(expenseId).firstOrNull() ?: return@launch
                val splits = expenseRepository.getExpenseSplitsFlow(expenseId).first()
                val mode = expense.splitType.toSplitMethod()

                updateState {
                    AddExpenseState(
                        amount =
                            expense.amount
                                .toMoneyDouble()
                                .toInt()
                                .toString(),
                        category = expense.category,
                        description = expense.title,
                        date = expense.date.toEpochMillis(),
                        payerId = participants.find { it.name == expense.payerName }?.id,
                        splitMethod = mode,
                        participants =
                            participants.map { p ->
                                val existingSplit = splits.find { it.participantId == p.userId }
                                ParticipantSplitState(
                                    participant = p,
                                    isSelected = existingSplit != null,
                                    inputValue =
                                        existingSplit?.let {
                                            displayInputValue(
                                                mode,
                                                it.value,
                                                it.amount,
                                            )
                                        } ?: "",
                                )
                            },
                        imageUrl = expense.imageUrl,
                        currency = currency,
                    )
                }
                editingExpenseId = expenseId
            }
            hasInitialized = true
            recalculate()
        }
    }

    private fun bootstrapDefaults(
        participants: List<Participant>,
        currency: String,
    ): AddExpenseState {
        val payerId = pickDefaultPayerId(participants)
        return AddExpenseState(
            participants =
                participants.map {
                    ParticipantSplitState(participant = it, isSelected = true)
                },
            payerId = payerId,
            currency = currency,
        )
    }

    private fun pickDefaultPayerId(participants: List<Participant>): Long? {
        val currentUser = userSession.currentUser.value
        val myParticipantId =
            currentUser?.let { user -> participants.find { it.userId == user.id }?.id }
        return myParticipantId ?: participants.firstOrNull()?.id
    }

    private fun syncParticipants(latest: List<Participant>) {
        val current = currentState
        if (current.participants.isEmpty() && latest.isEmpty()) return

        val previousById = current.participants.associateBy { it.participant.id }
        val merged =
            latest.map { p ->
                previousById[p.id]?.copy(participant = p)
                    ?: ParticipantSplitState(participant = p, isSelected = true)
            }
        val payerStillPresent = current.payerId != null && latest.any { it.id == current.payerId }
        val newPayerId =
            if (payerStillPresent) current.payerId else pickDefaultPayerId(latest)

        if (merged == current.participants && newPayerId == current.payerId) return

        updateState { copy(participants = merged, payerId = newPayerId) }
        recalculate()
    }

    private fun onSplitMethodChange(method: SplitMethod) {
        val oldMethod = currentState.splitMethod
        if (oldMethod == method) return

        val total = currentState.amount.toDoubleOrNull() ?: 0.0
        val seeded = seedForMode(oldMethod, method, currentState.participants, total)
        updateState { copy(splitMethod = method, participants = seeded) }
        recalculate()
    }

    private fun seedForMode(
        oldMethod: SplitMethod,
        newMethod: SplitMethod,
        participants: List<ParticipantSplitState>,
        total: Double,
    ): List<ParticipantSplitState> {
        val activeCount = participants.count { it.isSelected }

        fun distributeWithRemainder(
            per: Double,
            sumTarget: Double,
        ): List<Pair<Int, Double>> {
            val selectedIdx =
                participants.withIndex().filter { it.value.isSelected }.map { it.index }
            if (selectedIdx.isEmpty()) return emptyList()
            val floored = (per * 100).toInt() / 100.0
            val results = selectedIdx.map { it to floored }.toMutableList()
            val current = floored * selectedIdx.size
            val drift = sumTarget - current
            if (drift != 0.0 && results.isNotEmpty()) {
                val last = results.last()
                results[results.size - 1] = last.first to round((last.second + drift) * 100) / 100
            }
            return results
        }

        return when (newMethod) {
            SplitMethod.EQUAL -> {
                participants.map { it.copy(inputValue = "") }
            }

            SplitMethod.EXACT_AMOUNT -> {
                if (oldMethod == SplitMethod.EQUAL && activeCount > 0) {
                    val share = (total / activeCount).toInt().toString()
                    participants.map { if (it.isSelected) it.copy(inputValue = share) else it }
                } else {
                    participants.map {
                        if (it.isSelected) {
                            it.copy(
                                inputValue = it.calculatedAmount.toInt().toString(),
                            )
                        } else {
                            it
                        }
                    }
                }
            }

            SplitMethod.PERCENTAGE -> {
                if (activeCount == 0) {
                    participants.map { it.copy(inputValue = "") }
                } else if (oldMethod == SplitMethod.EXACT_AMOUNT && total > 0.0) {
                    val byIdx =
                        participants.withIndex().filter { it.value.isSelected }.map { (idx, p) ->
                            val v = (p.inputValue.toDoubleOrNull() ?: 0.0)
                            idx to (round(100.0 * v / total * 100) / 100)
                        }
                    val drift = 100.0 - byIdx.sumOf { it.second }
                    val adjusted = byIdx.toMutableList()
                    if (adjusted.isNotEmpty() && drift != 0.0) {
                        val last = adjusted.last()
                        adjusted[adjusted.size - 1] =
                            last.first to (round((last.second + drift) * 100) / 100)
                    }
                    val map = adjusted.toMap()
                    participants.mapIndexed { i, p ->
                        if (p.isSelected) p.copy(inputValue = formatNumber(map[i] ?: 0.0)) else p
                    }
                } else {
                    val seeded = distributeWithRemainder(100.0 / activeCount, 100.0).toMap()
                    participants.mapIndexed { i, p ->
                        if (p.isSelected) p.copy(inputValue = formatNumber(seeded[i] ?: 0.0)) else p
                    }
                }
            }

            SplitMethod.SHARES -> {
                participants.map { if (it.isSelected) it.copy(inputValue = "1") else it }
            }
        }
    }

    private fun formatNumber(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else (round(d * 100) / 100).toString()

    private fun displayInputValue(
        mode: SplitMethod,
        storedValue: String,
        storedAmount: String,
    ): String =
        when (mode) {
            SplitMethod.EQUAL -> ""
            SplitMethod.EXACT_AMOUNT -> storedAmount.toMoneyDouble().toInt().toString()
            SplitMethod.PERCENTAGE -> formatNumber(storedValue.toDoubleOrNull() ?: 0.0)
            SplitMethod.SHARES -> storedValue.trim().ifEmpty { "0" }
        }

    private fun onAmountChange(valStr: String) {
        if (valStr.all { it.isDigit() || it == '.' }) {
            updateState {
                copy(
                    amount = valStr,
                    amountError = if (showErrors) computeAmountError(valStr) else amountError,
                )
            }
            recalculate()
        }
    }

    private fun computeAmountError(value: String): String? = if (!Validation.isPositiveAmount(value)) "Введите сумму больше 0" else null

    private fun computeDescriptionError(value: String): String? = if (value.isBlank()) "Введите описание" else null

    private fun computePayerError(id: Long?): String? = if (id == null) "Выберите, кто оплатил" else null

    private fun computeParticipantsError(participants: List<ParticipantSplitState>): String? =
        if (participants.none { it.isSelected }) "Выберите хотя бы одного участника" else null

    private fun onInputValueChange(
        id: Long,
        valStr: String,
    ) {
        if (!valStr.all { it.isDigit() || it == '.' }) return

        val s = currentState
        val withTyped =
            s.participants.map {
                if (it.participant.id == id) {
                    it.copy(inputValue = valStr, isSelected = true)
                } else {
                    it
                }
            }

        val updated =
            if (s.splitMethod == SplitMethod.PERCENTAGE) {
                rebalancePercentages(withTyped, editedId = id)
            } else {
                withTyped
            }

        updateState { copy(participants = updated) }
        recalculate()
    }

    private fun rebalancePercentages(
        participants: List<ParticipantSplitState>,
        editedId: Long,
    ): List<ParticipantSplitState> {
        val typed =
            (
                participants.find { it.participant.id == editedId }?.inputValue?.toDoubleOrNull()
                    ?: 0.0
            ).coerceIn(0.0, 100.0)
        val others = participants.filter { it.isSelected && it.participant.id != editedId }
        if (others.isEmpty()) return participants

        val remaining = (100.0 - typed).coerceAtLeast(0.0)
        val perOther = remaining / others.size
        val perOtherFloored = (perOther * 100).toLong() / 100.0
        val drift = remaining - perOtherFloored * others.size

        val lastOtherId = others.last().participant.id
        return participants.map { p ->
            when {
                p.participant.id == editedId -> {
                    p
                }

                !p.isSelected -> {
                    p
                }

                p.participant.id == lastOtherId -> {
                    p.copy(inputValue = formatNumber(round((perOtherFloored + drift) * 100) / 100))
                }

                else -> {
                    p.copy(inputValue = formatNumber(perOtherFloored))
                }
            }
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
        val activeCount = s.participants.count { it.isSelected }

        if (activeCount == 0) {
            val cleared =
                s.participants.map { it.copy(calculatedAmount = 0.0) }
            updateState {
                copy(
                    participants = cleared,
                    isSplitValid = false,
                    splitError = "Выберите участников",
                    participantsError = if (showErrors) "Выберите хотя бы одного участника" else participantsError,
                )
            }
            return
        }

        when (s.splitMethod) {
            SplitMethod.EQUAL -> {
                val share = total / activeCount
                val updated =
                    s.participants.map {
                        it.copy(calculatedAmount = if (it.isSelected) share else 0.0)
                    }
                updateState {
                    copy(
                        participants = updated,
                        isSplitValid = true,
                        splitError = null,
                        participantsError = null,
                    )
                }
            }

            SplitMethod.EXACT_AMOUNT -> {
                val updated =
                    s.participants.map {
                        val amt = it.inputValue.toDoubleOrNull() ?: 0.0
                        it.copy(calculatedAmount = if (it.isSelected) amt else 0.0)
                    }
                val sumOfManual = updated.filter { it.isSelected }.sumOf { it.calculatedAmount }
                val diff = total - sumOfManual
                val isValid = abs(diff) < 0.01
                val errorMsg =
                    when {
                        diff > 0 -> "Не хватает: ${diff.toInt()}"
                        diff < 0 -> "Превышение на: ${abs(diff).toInt()}"
                        else -> null
                    }
                updateState {
                    copy(
                        participants = updated,
                        isSplitValid = isValid,
                        splitError = errorMsg,
                        participantsError = null,
                    )
                }
            }

            SplitMethod.PERCENTAGE -> {
                val updated =
                    s.participants.map {
                        val pct = it.inputValue.toDoubleOrNull() ?: 0.0
                        val amount = if (it.isSelected) total * pct / 100.0 else 0.0
                        it.copy(calculatedAmount = amount)
                    }
                val sumPct =
                    updated
                        .filter { it.isSelected }
                        .sumOf { it.inputValue.toDoubleOrNull() ?: 0.0 }
                val diff = 100.0 - sumPct
                val isValid = abs(diff) < 0.01
                val errorMsg =
                    when {
                        diff > 0 -> "Не хватает ${formatNumber(diff)}%"
                        diff < 0 -> "Превышение на ${formatNumber(abs(diff))}%"
                        else -> null
                    }
                updateState {
                    copy(
                        participants = updated,
                        isSplitValid = isValid,
                        splitError = errorMsg,
                        participantsError = null,
                    )
                }
            }

            SplitMethod.SHARES -> {
                val totalShares =
                    s.participants
                        .filter { it.isSelected }
                        .sumOf { it.inputValue.toDoubleOrNull() ?: 0.0 }
                val updated =
                    s.participants.map {
                        val share = it.inputValue.toDoubleOrNull() ?: 0.0
                        val amount =
                            if (it.isSelected && totalShares > 0) {
                                total * share / totalShares
                            } else {
                                0.0
                            }
                        it.copy(calculatedAmount = amount)
                    }
                val isValid = totalShares > 0
                val errorMsg = if (!isValid) "Введите долю хотя бы для одного" else null
                updateState {
                    copy(
                        participants = updated,
                        isSplitValid = isValid,
                        splitError = errorMsg,
                        participantsError = null,
                    )
                }
            }
        }
    }

    private fun save() {
        val s = currentState

        val amountError = computeAmountError(s.amount)
        val descriptionError = computeDescriptionError(s.description)
        val payerError = computePayerError(s.payerId)
        val participantsError = computeParticipantsError(s.participants)

        val hasFieldErrors =
            amountError != null ||
                descriptionError != null ||
                payerError != null ||
                participantsError != null

        if (hasFieldErrors || !s.isSplitValid) {
            updateState {
                copy(
                    showErrors = true,
                    amountError = amountError,
                    descriptionError = descriptionError,
                    payerError = payerError,
                    participantsError = participantsError,
                )
            }
            sendEffect(ExpenseFormEffect.ShowError("Заполните обязательные поля"))
            return
        }

        val total = s.amount.toDoubleOrNull() ?: 0.0
        val selectedPayerId = s.payerId!!
        val splitTypeStr = s.splitMethod.toServerString()

        val splitInputs =
            s.participants
                .filter { it.isSelected }
                .map { participant ->
                    val amountStr = participant.calculatedAmount.toMoneyString()
                    val valueStr =
                        when (s.splitMethod) {
                            SplitMethod.EQUAL -> {
                                "0"
                            }

                            SplitMethod.EXACT_AMOUNT -> {
                                amountStr
                            }

                            SplitMethod.PERCENTAGE -> {
                                (participant.inputValue.toDoubleOrNull() ?: 0.0).toMoneyString()
                            }

                            SplitMethod.SHARES -> {
                                participant.inputValue.trim().ifEmpty { "0" }
                            }
                        }
                    SplitInput(
                        localParticipantId = participant.participant.id,
                        value = valueStr,
                        amount = amountStr,
                    )
                }

        screenModelScope.launch {
            try {
                if (editingExpenseId == null) {
                    expenseRepository.addExpense(
                        tripId = tripId,
                        title = s.description,
                        amount = total,
                        category = s.category,
                        payerLocalId = selectedPayerId,
                        splitType = splitTypeStr,
                        splits = splitInputs,
                        photoBytes = s.photoBytes,
                    )
                } else {
                    expenseRepository.updateExpense(
                        tripId = tripId,
                        expenseLocalId = editingExpenseId!!,
                        title = s.description,
                        amount = total,
                        category = s.category,
                        payerLocalId = selectedPayerId,
                        splitType = splitTypeStr,
                        splits = splitInputs,
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
