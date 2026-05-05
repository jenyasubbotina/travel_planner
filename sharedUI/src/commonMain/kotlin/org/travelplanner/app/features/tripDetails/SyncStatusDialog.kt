package org.travelplanner.app.features.tripDetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.travelplanner.app.OutboxEntry

internal val SyncWarnIcon = Color(0xFFC2410C)
internal val SyncDangerText = Color(0xFF991B1B)
internal val SyncSubtle = Color(0xFF6B7280)
internal val SyncInfoText = Color(0xFF1E40AF)

@Composable
internal fun OutboxStatusDialog(
    conflicts: List<OutboxEntry>,
    deadEntries: List<OutboxEntry>,
    onDismiss: () -> Unit,
    onDiscardDead: (entryId: String) -> Unit,
    onRetryDead: (entryId: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Несинхронизированные изменения") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (conflicts.isNotEmpty()) {
                    Text(
                        "Конфликты (${conflicts.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = SyncWarnIcon,
                    )
                    conflicts.forEach { entry ->
                        if (entry.entityType == "EXPENSE") {
                            OutboxEntryRow(
                                entry = entry,
                                hint = "Откройте раздел «Расходы» - там доступны опции принять/изменить/откатить.",
                            )
                        } else {
                            DeadEntryRow(
                                entry = entry,
                                onDiscard = { onDiscardDead(entry.id) },
                                onRetry = { onRetryDead(entry.id) },
                            )
                        }
                    }
                }
                if (deadEntries.isNotEmpty()) {
                    Text(
                        "Не удалось отправить (${deadEntries.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = SyncDangerText,
                    )
                    deadEntries.forEach { entry ->
                        DeadEntryRow(
                            entry = entry,
                            onDiscard = { onDiscardDead(entry.id) },
                            onRetry = { onRetryDead(entry.id) },
                        )
                    }
                }
                if (conflicts.isEmpty() && deadEntries.isEmpty()) {
                    Text(
                        "Все изменения находятся в очереди - продолжайте пользоваться приложением, синхронизация произойдёт автоматически.",
                        color = SyncSubtle,
                        fontSize = 13.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

@Composable
private fun OutboxEntryRow(
    entry: OutboxEntry,
    hint: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
                .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.SyncProblem,
                contentDescription = null,
                tint = SyncSubtle,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${entryTypeLabel(entry.entityType)} · ${operationLabel(entry.operation)}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text = hint, fontSize = 12.sp, color = SyncSubtle)
    }
}

@Composable
private fun DeadEntryRow(
    entry: OutboxEntry,
    onDiscard: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
                .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.SyncProblem,
                contentDescription = null,
                tint = SyncSubtle,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${entryTypeLabel(entry.entityType)} · ${operationLabel(entry.operation)}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.lastError ?: "Сервер отклонил запрос.",
            fontSize = 12.sp,
            color = SyncSubtle,
        )
        Spacer(Modifier.height(4.dp))
        Text(text = reEditHint(entry.entityType), fontSize = 12.sp, color = SyncSubtle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onDiscard) { Text("Удалить", color = SyncDangerText) }
            TextButton(onClick = onRetry) { Text("Повторить", color = SyncInfoText) }
        }
    }
}

private fun reEditHint(entityType: String): String =
    when (entityType) {
        "EXPENSE" -> "Откройте раздел «Расходы» и измените запись повторно - новое изменение отправится автоматически."
        "EVENT" -> "Откройте раздел «Маршрут» и отредактируйте событие - новое изменение отправится автоматически."
        "TRIP" -> "Измените поездку повторно - новое изменение отправится автоматически."
        "CHECKLIST" -> "Откройте раздел «Чек-лист» и пересоздайте пункт."
        "PARTICIPANT_ROLE" -> "Откройте список участников и измените роль повторно."
        "POINT_COMMENT" -> "Откройте событие и отправьте комментарий заново."
        "ATTACHMENT" -> "Прикрепите файл повторно - новая загрузка отправится автоматически."
        else -> "После удаления запись можно отредактировать заново."
    }

private fun entryTypeLabel(t: String): String =
    when (t) {
        "TRIP" -> "Поездка"
        "EXPENSE" -> "Расход"
        "EVENT" -> "Событие"
        "CHECKLIST" -> "Чек-лист"
        "PARTICIPANT_ROLE" -> "Роль участника"
        "POINT_COMMENT" -> "Комментарий"
        "ATTACHMENT" -> "Вложение"
        else -> t
    }

private fun operationLabel(op: String): String =
    when (op) {
        "CREATE" -> "создание"
        "UPDATE" -> "изменение"
        "DELETE" -> "удаление"
        "TOGGLE" -> "переключение"
        else -> op
    }
