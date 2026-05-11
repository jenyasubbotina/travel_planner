package org.travelplanner.app.features.tripDetails.history.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.features.tripDetails.expenses.humanSplitType

object HistoryDiffUtils {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    internal fun parsePayload(rawJson: String): JsonObject? =
        runCatching {
            jsonParser.parseToJsonElement(rawJson).jsonObject
        }.getOrNull()

    fun generateDiffText(
        actionType: String,
        entityType: String,
        rawJson: String,
        participants: List<Participant>,
    ): List<String> {
        val payload = parsePayload(rawJson) ?: return emptyList()
        val entity = payload["entity"]?.jsonObject
        val old = payload["old"]?.jsonObject
        val new = payload["new"]?.jsonObject
        val context = payload["context"]?.jsonObject

        val userNames: (String?) -> String = { id ->
            if (id == null) {
                "Неизвестно"
            } else {
                participants.find { it.userId == id }?.name ?: "Удаленный участник"
            }
        }

        return when (actionType) {
            "CREATE" -> {
                renderCreate(entityType, entity, context, payload)
            }

            "UPDATE" -> {
                renderUpdate(entityType, old, new, userNames)
            }

            "DELETE" -> {
                renderDelete(entityType, entity, context)
            }

            "CHANGE_ROLE" -> {
                renderChangeRole(context)
            }

            "COMPLETE" -> {
                context
                    ?.string("title")
                    ?.let { listOf("Отмечено выполненным: $it") }
                    .orEmpty()
            }

            "UNCOMPLETE" -> {
                context
                    ?.string("title")
                    ?.let { listOf("Снято с выполнения: $it") }
                    .orEmpty()
            }

            "INVITE" -> {
                renderInvite(context)
            }

            "JOIN" -> {
                renderJoin(context)
            }

            "REQUEST_JOIN" -> {
                renderRequestJoin(context)
            }

            "APPROVE_JOIN" -> {
                renderApproveJoin(context, userNames)
            }

            "DENY_JOIN" -> {
                renderDenyJoin(context, userNames)
            }

            "REGENERATE_JOIN_CODE" -> {
                emptyList()
            }

            "REORDER_ITINERARY" -> {
                context
                    ?.intField("count")
                    ?.let { listOf("Точек переупорядочено: $it") }
                    .orEmpty()
            }

            "ARCHIVE" -> {
                emptyList()
            }

            "STORE_PENDING_UPDATE" -> {
                renderStorePending(context)
            }

            "REJECT_PENDING_UPDATE" -> {
                context
                    ?.string("title")
                    ?.let { listOf("Расход: $it") }
                    .orEmpty()
            }

            else -> {
                emptyList()
            }
        }
    }

    private fun renderCreate(
        entityType: String,
        entity: JsonObject?,
        context: JsonObject?,
        legacy: JsonObject,
    ): List<String> {
        if (entity == null) {
            val legacyTitle = legacy.string("title") ?: legacy.string("name")
            return if (legacyTitle != null) listOf("Добавлено: $legacyTitle") else emptyList()
        }
        return when (entityType) {
            "EXPENSE" -> {
                buildList {
                    entity.string("title")?.let { add("Описание: $it") }
                    entity.string("amount")?.let { amount ->
                        val currency = entity.string("currency") ?: "JPY"
                        add("Сумма: ${formatCurrency(amount, currency)}")
                    }
                    entity.string("category")?.let { add("Категория: ${categoryLabel(it)}") }
                }
            }

            "EVENT" -> {
                buildList {
                    entity.string("title")?.let { add("Название: $it") }
                    entity.string("date")?.let { add("Дата: $it") }
                    entity.string("category")?.let { add("Категория: $it") }
                    entity.string("address")?.let { add("Адрес: $it") }
                }
            }

            "TRIP" -> {
                buildList {
                    entity.string("title")?.let { add("Название: $it") }
                    entity.string("destination")?.let { if (it.isNotBlank()) add("Направление: $it") }
                    entity.string("totalBudget")?.let {
                        add(
                            "Бюджет: ${
                                formatCurrency(
                                    it,
                                    entity.string("baseCurrency") ?: "JPY",
                                )
                            }",
                        )
                    }
                }
            }

            "CHECKLIST_ITEM" -> {
                buildList {
                    entity
                        .string("title")
                        ?.let { add("Пункт: $it" + if (entity.boolField("isGroup") == true) " (групповой)" else "") }
                }
            }

            "ATTACHMENT" -> {
                buildList {
                    entity.string("fileName")?.let { add("Файл: $it") }
                }
            }

            "LINK", "COMMENT", "PARTICIPANT" -> {
                contextLines(context, entityType, "CREATE")
            }

            else -> {
                emptyList()
            }
        }.ifEmpty { contextLines(context, entityType, "CREATE") }
    }

    private fun renderUpdate(
        entityType: String,
        old: JsonObject?,
        new: JsonObject?,
        userNames: (String?) -> String,
    ): List<String> {
        if (old == null || new == null) return emptyList()
        return when (entityType) {
            "EXPENSE", "PAYMENT" -> {
                renderExpenseUpdate(old, new, userNames)
            }

            "EVENT" -> {
                renderEventUpdate(old, new, userNames)
            }

            "TRIP" -> {
                renderTripUpdate(old, new)
            }

            "CHECKLIST_ITEM" -> {
                val titleNew = new.string("title")
                if (titleNew != null) listOf("Название: ${old.string("title") ?: "—"} ➝ $titleNew") else emptyList()
            }

            else -> {
                emptyList()
            }
        }.ifEmpty { listOf("Изменены прочие детали") }
    }

    private fun renderExpenseUpdate(
        old: JsonObject,
        new: JsonObject,
        userNames: (String?) -> String,
    ): List<String> =
        buildList {
            diffString(old, new, "title")?.let { (o, n) -> add("Описание: $o ➝ $n") }
            diffString(old, new, "amount")?.let { (o, n) ->
                val currency = (new.string("currency") ?: old.string("currency") ?: "JPY")
                add("Сумма: ${formatCurrency(o, currency)} ➝ ${formatCurrency(n, currency)}")
            }
            diffString(old, new, "currency")?.let { (o, n) -> add("Валюта: $o ➝ $n") }
            diffString(
                old,
                new,
                "category",
            )?.let { (o, n) -> add("Категория: ${categoryLabel(o)} ➝ ${categoryLabel(n)}") }
            diffString(old, new, "expenseDate")?.let { (o, n) -> add("Дата: $o ➝ $n") }
            diffString(old, new, "splitType")?.let { (o, n) ->
                add("Тип разделения: ${humanSplitType(o)} ➝ ${humanSplitType(n)}")
            }
            diffString(
                old,
                new,
                "payerUserId",
            )?.let { (o, n) -> add("Оплатил: ${userNames(o)} ➝ ${userNames(n)}") }
            diffString(old, new, "description")?.let { (o, n) -> add("Заметка: $o ➝ $n") }
            if (old["splits"] != null && new["splits"] != null && old["splits"] != new["splits"]) {
                add("Изменено распределение долгов")
            }
        }

    private fun renderEventUpdate(
        old: JsonObject,
        new: JsonObject,
        userNames: (String?) -> String,
    ): List<String> =
        buildList {
            diffString(old, new, "title")?.let { (o, n) -> add("Название: $o ➝ $n") }
            diffString(old, new, "description")?.let { (o, n) -> add("Описание: $o ➝ $n") }
            diffString(old, new, "subtitle")?.let { (o, n) -> add("Подзаголовок: $o ➝ $n") }
            diffString(old, new, "category")?.let { (o, n) -> add("Категория: $o ➝ $n") }
            diffString(old, new, "date")?.let { (o, n) -> add("Дата: $o ➝ $n") }
            diffInt(old, new, "dayIndex")?.let { (o, n) -> add("День маршрута: $o ➝ $n") }
            diffString(old, new, "startTime")?.let { (o, n) -> add("Начало: $o ➝ $n") }
            diffString(old, new, "endTime")?.let { (o, n) -> add("Окончание: $o ➝ $n") }
            diffString(old, new, "address")?.let { (o, n) -> add("Адрес: $o ➝ $n") }
            diffDouble(
                old,
                new,
                "cost",
            )?.let { (o, n) -> add("Стоимость: ${formatNumber(o)} ➝ ${formatNumber(n)}") }
            diffDouble(
                old,
                new,
                "actualCost",
            )?.let { (o, n) -> add("Факт. стоимость: ${formatNumber(o)} ➝ ${formatNumber(n)}") }
            diffString(old, new, "status")?.let { (o, n) -> add("Статус: $o ➝ $n") }
            if (old["participantIds"] != null || new["participantIds"] != null) {
                val oldSize = old["participantIds"]?.jsonArray?.size ?: 0
                val newSize = new["participantIds"]?.jsonArray?.size ?: 0
                if (oldSize != newSize || old["participantIds"] != new["participantIds"]) {
                    add("Участники: $oldSize ➝ $newSize")
                }
            }
        }

    private fun renderTripUpdate(
        old: JsonObject,
        new: JsonObject,
    ): List<String> =
        buildList {
            diffString(old, new, "title")?.let { (o, n) -> add("Название: $o ➝ $n") }
            diffString(old, new, "description")?.let { (o, n) -> add("Описание: $o ➝ $n") }
            diffString(old, new, "destination")?.let { (o, n) -> add("Направление: $o ➝ $n") }
            diffString(old, new, "startDate")?.let { (o, n) -> add("Начало: $o ➝ $n") }
            diffString(old, new, "endDate")?.let { (o, n) -> add("Конец: $o ➝ $n") }
            diffString(old, new, "baseCurrency")?.let { (o, n) -> add("Валюта: $o ➝ $n") }
            diffString(old, new, "totalBudget")?.let { (o, n) ->
                val currency = (new.string("baseCurrency") ?: old.string("baseCurrency") ?: "JPY")
                add("Бюджет: ${formatCurrency(o, currency)} ➝ ${formatCurrency(n, currency)}")
            }
            diffString(old, new, "imageUrl")?.let { add("Обложка изменена") }
            diffString(old, new, "status")?.let { (o, n) -> add("Статус: $o ➝ $n") }
        }

    private fun renderDelete(
        entityType: String,
        entity: JsonObject?,
        context: JsonObject?,
    ): List<String> {
        if (entity == null) {
            return contextLines(
                context,
                entityType,
                "DELETE",
            ).ifEmpty { listOf("Запись была удалена") }
        }
        val summary =
            when (entityType) {
                "EXPENSE" -> {
                    val title = entity.string("title") ?: "—"
                    val amount = entity.string("amount")
                    val currency = entity.string("currency") ?: "JPY"
                    if (amount != null) "$title (${formatCurrency(amount, currency)})" else title
                }

                "EVENT" -> {
                    val title = entity.string("title") ?: "—"
                    val date = entity.string("date")
                    if (date != null) "$title ($date)" else title
                }

                "TRIP" -> {
                    entity.string("title") ?: "—"
                }

                "CHECKLIST_ITEM" -> {
                    entity.string("title") ?: "—"
                }

                "ATTACHMENT" -> {
                    entity.string("fileName") ?: "—"
                }

                else -> {
                    entity.string("title") ?: entity.string("name") ?: "—"
                }
            }
        return listOf("Удалено: $summary")
    }

    private fun renderChangeRole(context: JsonObject?): List<String> {
        if (context == null) return emptyList()
        val oldRole = roleLabel(context.string("oldRole"))
        val newRole = roleLabel(context.string("newRole"))
        if (oldRole == null || newRole == null) return emptyList()
        val name = context.string("participantName")
        return if (name != null) {
            listOf("$name: $oldRole ➝ $newRole")
        } else {
            listOf("Роль: $oldRole ➝ $newRole")
        }
    }

    private fun renderInvite(context: JsonObject?): List<String> {
        if (context == null) return emptyList()
        return buildList {
            context.string("email")?.let { add("Приглашен: $it") }
            context.string("role")?.let { add("Роль: ${roleLabel(it) ?: it}") }
        }
    }

    private fun renderJoin(context: JsonObject?): List<String> {
        if (context == null) return emptyList()
        val via = context.string("via")
        val name = context.string("participantName")
        return when {
            name != null && via == "INVITATION" -> listOf("$name (по приглашению)")
            name != null -> listOf(name)
            else -> emptyList()
        }
    }

    private fun renderRequestJoin(context: JsonObject?): List<String> {
        if (context == null) return emptyList()
        val title = context.string("tripTitle") ?: return emptyList()
        return listOf("Поездка: $title")
    }

    private fun renderApproveJoin(
        context: JsonObject?,
        userNames: (String?) -> String,
    ): List<String> {
        if (context == null) return emptyList()
        val userId = context.string("participantUserId") ?: return emptyList()
        return listOf("Участник: ${userNames(userId)}")
    }

    private fun renderDenyJoin(
        context: JsonObject?,
        userNames: (String?) -> String,
    ): List<String> {
        if (context == null) return emptyList()
        val userId = context.string("requesterUserId") ?: return emptyList()
        return listOf("Запрос от: ${userNames(userId)}")
    }

    private fun renderStorePending(context: JsonObject?): List<String> {
        if (context == null) return emptyList()
        val title = context.string("title") ?: return emptyList()
        return buildList {
            add("Расход: $title")
            context
                .string("proposedAmount")
                ?.let { add("Предложенная сумма: ${formatCurrency(it, "JPY")}") }
        }
    }

    private fun contextLines(
        context: JsonObject?,
        entityType: String,
        action: String,
    ): List<String> {
        if (context == null) return emptyList()
        return when (entityType) {
            "LINK" -> {
                buildList {
                    context.string("title")?.let { add("Ссылка: $it") }
                    context.string("pointTitle")?.let { add("Событие: $it") }
                }
            }

            "COMMENT" -> {
                buildList {
                    context.string("textPreview")?.let { add("Комментарий: $it") }
                    context.string("pointTitle")?.let { add("Событие: $it") }
                }
            }

            "PARTICIPANT" -> {
                buildList {
                    context.string("participantName")?.let { add("Участник: $it") }
                }
            }

            else -> {
                emptyList()
            }
        }
    }

    private fun diffString(
        old: JsonObject,
        new: JsonObject,
        key: String,
    ): Pair<String, String>? {
        val o = old.string(key)
        val n = new.string(key)
        return if (o != null && n != null && o != n) o to n else null
    }

    private fun diffInt(
        old: JsonObject,
        new: JsonObject,
        key: String,
    ): Pair<Int, Int>? {
        val o = old.intField(key)
        val n = new.intField(key)
        return if (o != null && n != null && o != n) o to n else null
    }

    private fun diffDouble(
        old: JsonObject,
        new: JsonObject,
        key: String,
    ): Pair<Double, Double>? {
        val o = old.doubleField(key)
        val n = new.doubleField(key)
        return if (o != null && n != null && o != n) o to n else null
    }

    private fun JsonObject.string(key: String): String? {
        val p = this[key] as? JsonPrimitive ?: return null
        val content = p.content
        return if (content == "null") null else content
    }

    private fun JsonObject.intField(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.doubleField(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.boolField(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.let { p ->
            p.booleanOrNull ?: p.content.toBooleanStrictOrNull()
        }

    private fun categoryLabel(cat: String?): String =
        when (cat) {
            "FOOD" -> "Еда 🍔"
            "HOUSING" -> "Жилье 🏠"
            "TRANSPORT" -> "Транспорт 🚕"
            "TICKETS" -> "Билеты 🎫"
            "PAYMENT" -> "Перевод 💸"
            else -> cat ?: "Другое"
        }

    private fun roleLabel(role: String?): String? =
        when (role) {
            "OWNER" -> "Владелец"
            "EDITOR" -> "Редактор"
            "VIEWER" -> "Наблюдатель"
            null -> null
            else -> role
        }

    private fun currencySymbol(currency: String): String =
        when (currency.uppercase()) {
            "JPY" -> "¥"
            "USD" -> "$"
            "EUR" -> "€"
            "RUB" -> "₽"
            "GBP" -> "£"
            "CNY" -> "¥"
            else -> "$currency "
        }

    private fun formatCurrency(
        amount: String,
        currency: String,
    ): String {
        val numeric = amount.toDoubleOrNull() ?: return "$amount $currency"
        return currencySymbol(currency) + formatNumber(numeric)
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            val rounded = (value * 100).toLong() / 100.0
            rounded.toString()
        }
}
