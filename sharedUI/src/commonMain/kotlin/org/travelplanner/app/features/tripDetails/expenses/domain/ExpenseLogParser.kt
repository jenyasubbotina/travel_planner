package org.travelplanner.app.features.tripDetails.expenses.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.compose.getKoin
import org.koin.dsl.module
import org.travelplanner.app.HistoryLogEntity

object ExpenseLogParser {
    private val jsonParser: Json =
        Json {
            ignoreUnknownKeys = true
        }

    fun parseActionTitle(log: HistoryLogEntity): String {
        return when (log.actionType) {
            "CREATE" -> {
                "Создан расход"
            }

            "DELETE" -> {
                "Удален расход"
            }

            "UPDATE" -> {
                try {
                    val jsonObject = jsonParser.parseToJsonElement(log.details).jsonObject
                    val newObj = jsonObject["new"]?.jsonObject
                    val oldObj = jsonObject["old"]?.jsonObject

                    val newImageUrl = newObj?.get("imageUrl")?.jsonPrimitive?.contentOrNull
                    val oldImageUrl = oldObj?.get("imageUrl")?.jsonPrimitive?.contentOrNull

                    if (newImageUrl != null && oldImageUrl == null) {
                        return "Прикреплён чек"
                    } else if (newImageUrl != oldImageUrl) {
                        return "Обновлён чек"
                    }

                    "Обновлены детали"
                } catch (e: Exception) {
                    "Обновлены детали"
                }
            }

            else -> {
                "Изменения сохранены"
            }
        }
    }
}
