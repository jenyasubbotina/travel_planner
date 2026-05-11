package org.travelplanner.app.features.tripDetails.expenses

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.travelplanner.app.core.V2ExpensePendingUpdateResponse
import org.travelplanner.app.core.V2ExpenseSplitRequest
import org.travelplanner.app.core.V2MergeExpenseRequest
import org.travelplanner.app.domain.Expense
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.features.tripDetails.history.ui.ExpenseBaseSnapshot
import org.travelplanner.app.features.tripDetails.history.ui.ExpenseConflictUi
import org.travelplanner.app.features.tripDetails.history.ui.ExpenseSideSnapshot
import org.travelplanner.app.features.tripDetails.history.ui.MergeField
import org.travelplanner.app.features.tripDetails.history.ui.MergeRow
import org.travelplanner.app.features.tripDetails.history.ui.MergeSide

private val payloadJson = Json { ignoreUnknownKeys = true }

internal data class ResolvedPayloads(
    val mine: ConflictPayload,
    val theirs: ConflictPayload,
    val base: ConflictPayload?,
    val mineIsProposer: Boolean,
    val proposedAtIso: String,
    val proposerUserId: String,
    val proposerName: String,
    val winnerUserId: String,
    val winnerName: String,
)

internal data class ConflictPayload(
    val title: String,
    val description: String?,
    val amount: String,
    val category: String,
    val payerUserId: String,
    val expenseDate: String,
    val splitType: String,
    val splits: List<V2ExpenseSplitRequest>,
    val receiptUrl: String?,
    val updatedAtIso: String,
)

internal fun resolvePayloads(
    expense: Expense,
    pendingJson: String,
    currentUserId: String?,
    participants: List<Participant>,
): ResolvedPayloads? {
    val pending =
        runCatching {
            payloadJson.decodeFromString(V2ExpensePendingUpdateResponse.serializer(), pendingJson)
        }.getOrNull() ?: return null

    val pendingObj =
        runCatching {
            payloadJson.decodeFromString(JsonObject.serializer(), pending.payload)
        }.getOrNull() ?: return null

    val basePayload =
        pending.baseSnapshot?.let { snap ->
            runCatching { payloadJson.decodeFromString(JsonObject.serializer(), snap) }.getOrNull()
        }

    val serverPayload = expense.toConflictPayload()
    val pendingPayload =
        pendingObj
            .toConflictPayload(fallback = serverPayload)
            .copy(updatedAtIso = pending.proposedAt)

    val proposerName =
        participants.firstOrNull { it.userId == pending.proposedByUserId }?.name
            ?: "Неизвестный"
    val winnerName =
        participants.firstOrNull { it.userId == expense.payerName }?.name
            ?: expense.payerName

    val mineIsProposer = currentUserId != null && currentUserId == pending.proposedByUserId

    val (mine, theirs) =
        if (mineIsProposer) {
            pendingPayload to serverPayload
        } else {
            serverPayload to pendingPayload
        }

    return ResolvedPayloads(
        mine = mine,
        theirs = theirs,
        base = basePayload?.toConflictPayload(fallback = serverPayload),
        mineIsProposer = mineIsProposer,
        proposedAtIso = pending.proposedAt,
        proposerUserId = pending.proposedByUserId,
        proposerName = proposerName,
        winnerUserId = expense.payerName,
        winnerName = winnerName,
    )
}

internal fun ResolvedPayloads.toUi(
    expense: Expense,
    participants: List<Participant>,
    currency: String,
    formatIso: (String) -> String,
): ExpenseConflictUi {
    val byId = participants.associateBy { it.userId }

    fun displayName(id: String) = byId[id]?.name ?: id.take(8)

    val mineMeta =
        if (mineIsProposer) {
            proposerUserId to proposerName
        } else {
            expense.creatorUserId to
                displayName(
                    expense.creatorUserId,
                )
        }
    val theirsMeta =
        if (mineIsProposer) expense.creatorUserId to displayName(expense.creatorUserId) else proposerUserId to proposerName

    return ExpenseConflictUi(
        expenseShortId = expense.id.take(8),
        title = expense.title,
        conflictAtFormatted = formatIso(proposedAtIso),
        mine =
            mine.toSideSnapshot(
                participants = participants,
                currency = currency,
                modifiedAtIso = mine.updatedAtIso,
                modifiedByUserId = mineMeta.first,
                modifiedByName = mineMeta.second,
                formatIso = formatIso,
            ),
        theirs =
            theirs.toSideSnapshot(
                participants = participants,
                currency = currency,
                modifiedAtIso = theirs.updatedAtIso,
                modifiedByUserId = theirsMeta.first,
                modifiedByName = theirsMeta.second,
                formatIso = formatIso,
            ),
        base = base?.toBaseSnapshot(participants = participants, currency = currency),
    )
}

internal fun ResolvedPayloads.toMergeRows(
    participants: List<Participant>,
    currency: String,
): List<MergeRow> {
    val byId = participants.associateBy { it.userId }

    fun name(id: String) = byId[id]?.name ?: id.take(8)

    fun money(amount: String): String = "$currency${amount.toDoubleOrNull()?.let { formatMoney(it) } ?: amount}"

    fun splitsLabel(p: ConflictPayload): String = "${humanSplitType(p.splitType)} (${p.splits.size} чел)"

    return listOf(
        MergeRow(
            field = MergeField.AMOUNT,
            label = "Сумма",
            mineDisplay = money(mine.amount),
            theirsDisplay = money(theirs.amount),
            sameOnBothSides = mine.amount == theirs.amount,
        ),
        MergeRow(
            field = MergeField.CATEGORY,
            label = "Категория",
            mineDisplay = humanCategory(mine.category),
            theirsDisplay = humanCategory(theirs.category),
            sameOnBothSides = mine.category == theirs.category,
        ),
        MergeRow(
            field = MergeField.DESCRIPTION,
            label = "Описание",
            mineDisplay = mine.title,
            theirsDisplay = theirs.title,
            sameOnBothSides = mine.title == theirs.title,
        ),
        MergeRow(
            field = MergeField.PAYER,
            label = "Оплатил",
            mineDisplay = name(mine.payerUserId),
            theirsDisplay = name(theirs.payerUserId),
            sameOnBothSides = mine.payerUserId == theirs.payerUserId,
        ),
        MergeRow(
            field = MergeField.SPLITS,
            label = "Разделить",
            mineDisplay = splitsLabel(mine),
            theirsDisplay = splitsLabel(theirs),
            sameOnBothSides = mine.splitType == theirs.splitType && mine.splits == theirs.splits,
        ),
    )
}

internal fun ResolvedPayloads.toMergeRequest(choices: Map<MergeField, MergeSide>): V2MergeExpenseRequest {
    fun pick(field: MergeField): ConflictPayload = if (choices[field] == MergeSide.THEIRS) theirs else mine

    return V2MergeExpenseRequest(
        amount = pick(MergeField.AMOUNT).amount,
        category = pick(MergeField.CATEGORY).category,
        title = pick(MergeField.DESCRIPTION).title,
        payerUserId = pick(MergeField.PAYER).payerUserId,
        splitType = pick(MergeField.SPLITS).splitType,
        splits = pick(MergeField.SPLITS).splits,
        currency = mine.currencyOrFallback(theirs),
        expenseDate = mine.expenseDate.ifBlank { theirs.expenseDate },
    )
}

private fun ConflictPayload.currencyOrFallback(other: ConflictPayload): String = "JPY"

// ─── Conversions ──────────────────────────────────────────────────────────────

private fun Expense.toConflictPayload(): ConflictPayload =
    ConflictPayload(
        title = title,
        description = null,
        amount = amount,
        category = category,
        payerUserId = creatorUserId,
        expenseDate = date,
        splitType = splitType,
        splits = emptyList(),
        receiptUrl = imageUrl,
        updatedAtIso = date,
    )

private fun JsonObject.toConflictPayload(fallback: ConflictPayload): ConflictPayload {
    fun str(key: String): String? =
        this[key]?.jsonPrimitive?.let {
            if (it.isString) it.content else it.content.takeIf { c -> c != "null" }
        }

    val splits =
        this["splits"]
            ?.jsonArray
            ?.mapNotNull { entry ->
                val obj = entry.jsonObject
                val pid = obj["participantUserId"]?.jsonPrimitive?.content
                val v = obj["value"]?.jsonPrimitive?.content
                if (pid != null && v != null) V2ExpenseSplitRequest(pid, v) else null
            }.orEmpty()

    return ConflictPayload(
        title = str("title") ?: fallback.title,
        description = str("description") ?: fallback.description,
        amount = str("amount") ?: fallback.amount,
        category = str("category") ?: fallback.category,
        payerUserId = str("payerUserId") ?: fallback.payerUserId,
        expenseDate = str("expenseDate") ?: fallback.expenseDate,
        splitType = str("splitType") ?: fallback.splitType,
        splits = if (splits.isNotEmpty()) splits else fallback.splits,
        receiptUrl = fallback.receiptUrl,
        updatedAtIso = fallback.updatedAtIso,
    )
}

private fun ConflictPayload.toSideSnapshot(
    participants: List<Participant>,
    currency: String,
    modifiedAtIso: String,
    modifiedByUserId: String,
    modifiedByName: String,
    formatIso: (String) -> String,
): ExpenseSideSnapshot {
    val byId = participants.associateBy { it.userId }
    return ExpenseSideSnapshot(
        amountFormatted = formatAmount(currency, amount),
        category = humanCategory(category),
        description = title,
        payerName = byId[payerUserId]?.name ?: payerUserId.take(8),
        splitsText = "${humanSplitType(splitType)} (${splits.size.coerceAtLeast(participants.size)} чел)",
        receiptUrl = receiptUrl,
        modifiedAt = formatIso(modifiedAtIso),
        modifiedByUserId = modifiedByUserId,
        modifiedByName = modifiedByName,
    )
}

private fun ConflictPayload.toBaseSnapshot(
    participants: List<Participant>,
    currency: String,
): ExpenseBaseSnapshot {
    val byId = participants.associateBy { it.userId }
    return ExpenseBaseSnapshot(
        amountFormatted = formatAmount(currency, amount),
        category = humanCategory(category),
        description = title,
        payerName = byId[payerUserId]?.name ?: payerUserId.take(8),
        splitsText = "${humanSplitType(splitType)} (${splits.size.coerceAtLeast(participants.size)} чел)",
        receiptUrl = receiptUrl,
    )
}

private fun formatAmount(
    currency: String,
    amount: String,
): String {
    val n = amount.toDoubleOrNull() ?: return "$currency$amount"
    return "$currency${formatMoney(n)}"
}

private fun formatMoney(n: Double): String = if (n == n.toLong().toDouble()) "${n.toLong()}" else "${(kotlin.math.round(n * 100) / 100)}"

private fun humanCategory(raw: String): String =
    when (raw.uppercase()) {
        "FOOD" -> "🍱 Питание"
        "HOUSING" -> "🏠 Жильё"
        "TRANSPORT" -> "🚇 Транспорт"
        "FUN" -> "🎯 Развлечения"
        "SHOPPING" -> "🛍 Покупки"
        "PAYMENT" -> "💸 Платёж"
        else -> "📌 Прочее"
    }

internal fun humanSplitType(raw: String): String =
    when (raw.uppercase()) {
        "EQUAL" -> "Поровну"
        "EXACT_AMOUNT" -> "Вручную"
        "PERCENTAGE" -> "Проценты"
        "SHARES" -> "Доли"
        "MANUAL" -> "Вручную"
        else -> raw
    }
