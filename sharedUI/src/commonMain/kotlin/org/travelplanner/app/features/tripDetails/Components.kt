package org.travelplanner.app.features.tripDetails

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SyncIndicator(
    networkState: NetworkState,
    syncState: SyncState,
    retrySeconds: Int? = null,
    currentConfig: GatewayConfig = GatewayConfig(),
    onConfigSave: (GatewayConfig) -> Unit = {},
    onClick: () -> Unit,
) {
    var showGatewayDialog by remember { mutableStateOf(false) }

    val (color, text, icon) =
        when {
            networkState == NetworkState.OFFLINE && retrySeconds != null -> {
                Triple(
                    Color(0xFFEAB308),
                    "Повтор через ${retrySeconds}с",
                    Icons.Default.CloudOff,
                )
            }

            networkState == NetworkState.OFFLINE -> {
                Triple(Color.Red, "Офлайн", Icons.Default.CloudOff)
            }

            networkState == NetworkState.CONNECTING -> {
                Triple(
                    Color(0xFFEAB308),
                    "Соединение...",
                    Icons.Default.WifiTethering,
                )
            }

            syncState == SyncState.SYNCING -> {
                Triple(
                    Color(0xFF3B82F6),
                    "Синхронизация...",
                    Icons.Default.Sync,
                )
            }

            syncState == SyncState.ERROR -> {
                Triple(Color.Red, "Ошибка синхр.", Icons.Default.ErrorOutline)
            }

            else -> {
                Triple(Color(0xFF22C55E), "Синхронизировано", Icons.Default.CloudDone)
            }
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = {
                        if (syncState == SyncState.ERROR) {
                            onClick()
                        }
                    },
                    onLongClick = { showGatewayDialog = true },
                ).padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = if (networkState == NetworkState.CONNECTING || syncState == SyncState.SYNCING) 360f else 0f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                ),
        )

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier =
                Modifier
                    .size(16.dp)
                    .rotate(if (networkState == NetworkState.CONNECTING || syncState == SyncState.SYNCING) angle else 0f),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
