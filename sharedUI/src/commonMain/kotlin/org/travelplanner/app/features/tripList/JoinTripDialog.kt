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
fun JoinTripDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Присоединиться") },
        text = {
            Column {
                Text("Введите код приглашения (напр. X92-A1)", fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                DSTextInput(
                    value = code,
                    onValueChange = {
                        code =
                            it.uppercase().filter { char -> char.isLetterOrDigit() || char == '-' }
                    },
                    placeholder = "X92-A1",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            DSButton(
                text = "Отправить заявку",
                onClick = { if (code.isNotBlank()) onSubmit(code) },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
        containerColor = Color.White,
    )
}
