package org.travelplanner.app.features.tripList

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.travelplanner.app.core.Validation
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSTextInput

@Composable
fun AcceptInvitationDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    isOffline: Boolean = false,
) {
    var invitationId by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val invitationError = when {
        !showError -> null
        invitationId.isBlank() -> "Введите идентификатор приглашения"
        !Validation.isValidUuid(invitationId) -> "Неверный формат идентификатора"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить поездку") },
        text = {
            Column {
                Text(
                    text = "Вставьте код приглашения, который прислал организатор. Мы добавим его в список — вы сможете принять или отклонить.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                )
                Spacer(Modifier.height(12.dp))
                DSTextInput(
                    value = invitationId,
                    onValueChange = { invitationId = it.trim() },
                    placeholder = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                    isError = invitationError != null,
                    errorMessage = invitationError,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isOffline) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Требуется подключение к сети",
                        fontSize = 12.sp,
                        color = Color(0xFFEF4444),
                    )
                }
            }
        },
        confirmButton = {
            DSButton(
                text = "Добавить",
                onClick = {
                    if (Validation.isValidUuid(invitationId)) {
                        onSubmit(invitationId)
                    } else {
                        showError = true
                    }
                },
                enabled = !isOffline,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
        containerColor = Color.White,
    )
}
