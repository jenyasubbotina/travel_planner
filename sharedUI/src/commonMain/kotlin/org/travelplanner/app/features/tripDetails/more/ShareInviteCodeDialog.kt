package org.travelplanner.app.features.tripDetails.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.travelplanner.app.theme.DSButton

@Composable
fun ShareInviteCodeDialog(
    joinCode: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пригласить друзей") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Поделитесь этим кодом, чтобы друзья могли присоединиться к вашей поездке:",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )

                Box(
                    modifier =
                        Modifier
                            .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = joinCode ?: "ОШИБКА",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF155DFC),
                        letterSpacing = 2.sp,
                    )
                }
            }
        },
        confirmButton = {
            DSButton(
                text = "Готово",
                onClick = onDismiss,
            )
        },
        containerColor = Color.White,
    )
}
