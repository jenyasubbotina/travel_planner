package org.travelplanner.app.features.tripDetails

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.travelplanner.app.OutboxEntry
import org.travelplanner.app.core.GatewayConfig
import org.travelplanner.app.data.NetworkState
import org.travelplanner.app.data.SyncState
import org.travelplanner.app.theme.DSTextInput

@Composable
fun EditBudgetDialog(
    currentBudget: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var textValue by remember { mutableStateOf(currentBudget.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить бюджет") },
        text = {
            DSTextInput(
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = "0",
                label = "Сумма",
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newBudget = textValue.toDoubleOrNull()
                    if (newBudget != null && newBudget >= 0) {
                        onConfirm(newBudget)
                    }
                },
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.Gray)
            }
        },
    )
}

private data class IndicatorVisuals(
    val color: Color,
    val text: String,
    val icon: ImageVector,
    val badgeText: String?,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SyncIndicator(
    networkState: NetworkState,
    syncState: SyncState,
    pendingCount: Long = 0L,
    conflicts: List<OutboxEntry> = emptyList(),
    deadEntries: List<OutboxEntry> = emptyList(),
    depthAlert: Boolean = false,
    retrySeconds: Int? = null,
    pendingApprovalsCount: Long = 0L,
    pendingProposalsCount: Long = 0L,
    currentConfig: GatewayConfig = GatewayConfig(),
    onConfigSave: (GatewayConfig) -> Unit = {},
    onRetrySync: () -> Unit = {},
    onDiscardDeadEntry: (String) -> Unit = {},
    onRetryDeadEntry: (String) -> Unit = {},
) {
    var showGatewayDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }

    val attentionCount = conflicts.size + deadEntries.size + pendingApprovalsCount.toInt()
    val pendingBadge =
        when {
            pendingCount > 99 -> "99+"
            pendingCount > 0 -> pendingCount.toString()
            else -> null
        }
    val attentionBadge = if (attentionCount > 99) "99+" else attentionCount.toString()
    val proposalsBadge =
        when {
            pendingProposalsCount > 99 -> "99+"
            pendingProposalsCount > 0 -> pendingProposalsCount.toString()
            else -> null
        }

    val state =
        when {
            depthAlert -> {
                IndicatorVisuals(Color(0xFFEF4444), "Слишком много изменений", Icons.Default.ErrorOutline, null)
            }

            attentionCount > 0 -> {
                IndicatorVisuals(
                    Color(0xFFC2410C),
                    "$attentionCount требует внимания",
                    Icons.Default.WarningAmber,
                    attentionBadge,
                )
            }

            networkState == NetworkState.OFFLINE && retrySeconds != null -> {
                IndicatorVisuals(
                    Color(0xFFEAB308),
                    "Повтор через ${retrySeconds}с",
                    Icons.Default.CloudOff,
                    pendingBadge,
                )
            }

            networkState == NetworkState.OFFLINE && pendingCount > 0 -> {
                IndicatorVisuals(
                    Color(0xFFEF4444),
                    "Офлайн",
                    Icons.Default.CloudUpload,
                    pendingBadge,
                )
            }

            networkState == NetworkState.OFFLINE -> {
                IndicatorVisuals(Color.Red, "Офлайн", Icons.Default.CloudOff, null)
            }

            networkState == NetworkState.CONNECTING -> {
                IndicatorVisuals(
                    Color(0xFFEAB308),
                    "Соединение...",
                    Icons.Default.WifiTethering,
                    pendingBadge,
                )
            }

            syncState == SyncState.SYNCING -> {
                IndicatorVisuals(
                    Color(0xFF3B82F6),
                    "Синхронизация...",
                    Icons.Default.Sync,
                    pendingBadge,
                )
            }

            syncState == SyncState.ERROR -> {
                IndicatorVisuals(Color.Red, "Ошибка синхр.", Icons.Default.ErrorOutline, null)
            }

            pendingCount > 0 -> {
                IndicatorVisuals(
                    Color(0xFFC2410C),
                    "В очереди",
                    Icons.Default.CloudUpload,
                    pendingBadge,
                )
            }

            pendingProposalsCount > 0 -> {
                IndicatorVisuals(
                    Color(0xFF3B82F6),
                    "На рассмотрении: $pendingProposalsCount",
                    Icons.Default.HourglassEmpty,
                    proposalsBadge,
                )
            }

            else -> {
                IndicatorVisuals(Color(0xFF22C55E), "Синхронизировано", Icons.Default.CloudDone, null)
            }
        }

    val color = state.color
    val text = state.text
    val icon = state.icon
    val badgeText = state.badgeText

    val isAnimating = networkState == NetworkState.CONNECTING || syncState == SyncState.SYNCING

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = { showMenu = true },
                        onLongClick = { showGatewayDialog = true },
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            val infiniteTransition = rememberInfiniteTransition()
            val angle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = if (isAnimating) 360f else 0f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                    ),
            )

            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier =
                        Modifier
                            .size(16.dp)
                            .rotate(if (isAnimating) angle else 0f),
                )
                if (badgeText != null) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 5.dp, y = (-3).dp)
                                .defaultMinSize(minWidth = 12.dp, minHeight = 12.dp)
                                .background(color, CircleShape)
                                .padding(horizontal = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(if (badgeText != null) 8.dp else 4.dp))
            Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                },
                onClick = {},
                enabled = false,
            )
            HorizontalDivider()
            if (pendingCount > 0) {
                DropdownMenuItem(
                    text = { Text("В очереди: $pendingCount", fontSize = 13.sp) },
                    onClick = { showMenu = false },
                    enabled = false,
                )
            }
            if (pendingApprovalsCount > 0) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Ожидает одобрения: $pendingApprovalsCount",
                            fontSize = 13.sp,
                            color = SyncWarnIcon,
                        )
                    },
                    onClick = { showMenu = false },
                    enabled = false,
                )
            }
            if (pendingProposalsCount > 0) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "На рассмотрении: $pendingProposalsCount",
                            fontSize = 13.sp,
                            color = SyncInfoText,
                        )
                    },
                    onClick = { showMenu = false },
                    enabled = false,
                )
            }
            if (conflicts.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Конфликты: ${conflicts.size}",
                            fontSize = 13.sp,
                            color = SyncWarnIcon,
                        )
                    },
                    onClick = {
                        showMenu = false
                        showStatusDialog = true
                    },
                )
            }
            if (deadEntries.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Не удалось отправить: ${deadEntries.size}",
                            fontSize = 13.sp,
                            color = SyncDangerText,
                        )
                    },
                    onClick = {
                        showMenu = false
                        showStatusDialog = true
                    },
                )
            }
            if (pendingCount > 0 ||
                pendingApprovalsCount > 0 ||
                pendingProposalsCount > 0 ||
                conflicts.isNotEmpty() ||
                deadEntries.isNotEmpty()
            ) {
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text("Повторить синхронизацию", fontSize = 13.sp) },
                onClick = {
                    showMenu = false
                    onRetrySync()
                },
            )
            DropdownMenuItem(
                text = { Text("Адрес сервера", fontSize = 13.sp) },
                onClick = {
                    showMenu = false
                    showGatewayDialog = true
                },
            )
        }
    }

    if (showStatusDialog) {
        OutboxStatusDialog(
            conflicts = conflicts,
            deadEntries = deadEntries,
            onDismiss = { showStatusDialog = false },
            onDiscardDead = onDiscardDeadEntry,
            onRetryDead = onRetryDeadEntry,
        )
    }

    if (showGatewayDialog) {
        GatewayConfigDialog(
            currentConfig = currentConfig,
            onDismiss = { showGatewayDialog = false },
            onSave = { config ->
                showGatewayDialog = false
                onConfigSave(config)
            },
        )
    }
}

@Composable
fun GatewayConfigDialog(
    currentConfig: GatewayConfig,
    onDismiss: () -> Unit,
    onSave: (GatewayConfig) -> Unit,
) {
    var address by remember { mutableStateOf(currentConfig.address) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Адрес сервера") },
        text = {
            Column {
                DSTextInput(
                    value = address,
                    onValueChange = { address = it },
                    placeholder = GatewayConfig.DEFAULT_ADDRESS,
                    label = "Адрес (host:port)",
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Текущий: ${currentConfig.baseUrl}",
                    fontSize = 11.sp,
                    color = Color.Gray,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(GatewayConfig(address = address.trim()))
                },
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.Gray)
            }
        },
    )
}
