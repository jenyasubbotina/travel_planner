package org.travelplanner.app.features.tripDetails.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSTextInput

@Composable
fun InviteByEmailDialog(
    inFlight: Boolean,
    createdInvitationId: String?,
    onSend: (email: String, role: String) -> Unit,
    onAcknowledgeShared: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (createdInvitationId != null) {
        InvitationIdShareStep(
            invitationId = createdInvitationId,
            onDone = onAcknowledgeShared,
        )
    } else {
        EmailRoleStep(
            inFlight = inFlight,
            onSend = onSend,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun EmailRoleStep(
    inFlight: Boolean,
    onSend: (email: String, role: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("EDITOR") }

    val canSubmit = email.isNotBlank() && email.contains('@') && email.contains('.') && !inFlight

    AlertDialog(
        onDismissRequest = { if (!inFlight) onDismiss() },
        title = { Text("Пригласить по email") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Введите email друга и выберите роль.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                )
                DSTextInput(
                    value = email,
                    onValueChange = { email = it.trim() },
                    placeholder = "friend@example.com",
                    modifier = Modifier.fillMaxWidth(),
                )
                Column {
                    RoleOption(
                        label = "Редактор",
                        description = "Может добавлять события, расходы и файлы",
                        selected = role == "EDITOR",
                        onSelect = { role = "EDITOR" },
                    )
                    RoleOption(
                        label = "Наблюдатель",
                        description = "Только просмотр",
                        selected = role == "VIEWER",
                        onSelect = { role = "VIEWER" },
                    )
                }
            }
        },
        confirmButton = {
            DSButton(
                text = "Отправить",
                onClick = { if (canSubmit) onSend(email, role) },
                enabled = canSubmit,
                isLoading = inFlight,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !inFlight) { Text("Отмена") }
        },
        containerColor = Color.White,
    )
}

@Composable
private fun RoleOption(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            Text(description, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun InvitationIdShareStep(
    invitationId: String,
    onDone: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Приглашение создано") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Скопируйте код приглашения и отправьте его другу . " +
                        "После входа в приложение он введёт его в «По приглашению».",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = invitationId,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF155DFC),
                        textAlign = TextAlign.Center,
                    )
                }
                DSButton(
                    text = if (copied) "Скопировано" else "Копировать",
                    onClick = {
                        clipboard.setText(AnnotatedString(invitationId))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isSuccess = copied,
                )
            }
        },
        confirmButton = {
            DSButton(text = "Готово", onClick = onDone)
        },
        containerColor = Color.White,
    )
}
