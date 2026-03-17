package org.travelplanner.app.features.tripDetails.history.data

import kotlinx.serialization.json.*
import org.travelplanner.app.domain.Participant

object HistoryDiffUtils {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun generateDiffText(
        actionType: String,
        entityType: String,
        rawJson: String,
        participants: List<Participant>,
    ): List<String> {
        val changes = mutableListOf<String>()
        try {
            val jsonObject = jsonParser.parseToJsonElement(rawJson).jsonObject

            fun getUserName(id: String?): String {
                if (id == null) return "Неизвестно"
                return participants.find { it.userId == id }?.name ?: "Удаленный участник"
            }

            fun getCatName(cat: String?): String =
                when (cat) {
                    "FOOD" -> "Еда 🍔"
                    "HOUSING" -> "Жилье 🏠"
                    "TRANSPORT" -> "Транспорт 🚕"
                    "TICKETS" -> "Билеты 🎫"
                    "PAYMENT" -> "Перевод 💸"
                    else -> cat ?: "Другое"
                }

            when (actionType) {
                "CREATE" -> {
                    val title =
                        jsonObject["title"]?.jsonPrimitive?.content
                            ?: jsonObject["name"]?.jsonPrimitive?.content
                    if (title != null) changes.add("Добавлено: $title")
                }

                "UPDATE" -> {
                    val oldObj = jsonObject["old"]?.jsonObject ?: return emptyList()
                    val newObj = jsonObject["new"]?.jsonObject ?: return emptyList()

                    if (entityType == "EXPENSE" || entityType == "PAYMENT") {
                        val oldAmt = oldObj["amount"]?.jsonPrimitive?.doubleOrNull
                        val newAmt = newObj["amount"]?.jsonPrimitive?.doubleOrNull
                        if (oldAmt != newAmt && oldAmt != null && newAmt != null) {
                            changes.add("Сумма: ¥${oldAmt.toInt()} ➝ ¥${newAmt.toInt()}")
                        }

                        val oldTitle = oldObj["title"]?.jsonPrimitive?.content
                        val newTitle = newObj["title"]?.jsonPrimitive?.content
                        if (oldTitle != newTitle && oldTitle != null && newTitle != null) {
                            changes.add("Описание: $oldTitle ➝ $newTitle")
                        }

                        val oldCat = oldObj["category"]?.jsonPrimitive?.content
                        val newCat = newObj["category"]?.jsonPrimitive?.content
                        if (oldCat != newCat && oldCat != null && newCat != null) {
                            changes.add("Категория: ${getCatName(oldCat)} ➝ ${getCatName(newCat)}")
                        }

                        val oldPayer = oldObj["payerUserId"]?.jsonPrimitive?.content
                        val newPayer = newObj["payerUserId"]?.jsonPrimitive?.content
                        if (oldPayer != newPayer && oldPayer != null && newPayer != null) {
                            changes.add("Оплатил: ${getUserName(oldPayer)} ➝ ${getUserName(newPayer)}")
                        }

                        val oldSplits = oldObj["splits"]?.jsonArray?.toString()
                        val newSplits = newObj["splits"]?.jsonArray?.toString()
                        if (oldSplits != newSplits && oldSplits != null && newSplits != null) {
                            changes.add("Изменено распределение долгов")
                        }
                    }

                    if (entityType == "TRIP") {
                        val oldBudget = oldObj["totalBudget"]?.jsonPrimitive?.doubleOrNull
                        val newBudget = newObj["totalBudget"]?.jsonPrimitive?.doubleOrNull
                        if (oldBudget != newBudget && oldBudget != null && newBudget != null) {
                            changes.add("Бюджет: ¥${oldBudget.toInt()} ➝ ¥${newBudget.toInt()}")
                        }
                    }

                    if (entityType == "CHECKLIST") {
                        val title = newObj["title"]?.jsonPrimitive?.content
                        changes.add("Статус пункта: $title")
                    }
                }

                "DELETE" -> {
                    changes.add("Запись была удалена")
                }
            }
        } catch (e: Exception) {
            println("Failed to parse diff: ${e.message}")
        }

        if (changes.isEmpty() && actionType == "UPDATE") changes.add("Изменены прочие детали")
        return changes
    }
}
