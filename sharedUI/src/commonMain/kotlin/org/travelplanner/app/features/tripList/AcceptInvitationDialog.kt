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
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSTextInput

@Composable
fun AcceptInvitationDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var invitationId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Принять приглашение") },
        text = {
            Column {
                Text(
                    text = "Вставьте код приглашения, который прислал организатор поездки.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                )
                Spacer(Modifier.height(12.dp))
                DSTextInput(
                    value = invitationId,
                    onValueChange = { invitationId = it.trim() },
                    placeholder = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            DSButton(
                text = "Принять",
                onClick = { if (invitationId.isNotBlank()) onSubmit(invitationId) },
                enabled = invitationId.isNotBlank(),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
        containerColor = Color.White,
    )
}
