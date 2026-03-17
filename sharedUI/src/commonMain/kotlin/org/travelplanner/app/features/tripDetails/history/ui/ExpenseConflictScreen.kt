package org.travelplanner.app.features.tripDetails.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSNotificationBanner

data class ConflictDetail(
    val baseValue: String,
    val remoteValue: String,
)

data class ExpenseConflictState(
    val expenseId: String,
    val conflictDateStr: String,
    val title: String,
    val sum: ConflictDetail,
    val category: ConflictDetail,
    val description: ConflictDetail,
    val payer: ConflictDetail,
    val split: ConflictDetail,
    val remoteUserName: String,
)

@Composable
fun ExpenseConflictScreen(
    state: ExpenseConflictState,
    onAcceptProposed: () -> Unit,
    onKeepCurrent: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFFAFAFA), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DSNotificationBanner(
            title = "Запрос на изменение",
            text = "Участник ${state.remoteUserName} предложил изменения в ваш расход. Вы можете принять их или отклонить.",
            backgroundColor = Color(0xFFFFF7ED),
            borderColor = Color(0xFFFFEDD5),
            contentColor = Color(0xFF9A3412),
            iconColor = Color(0xFFC2410C),
            icon = Icons.Default.WarningAmber,
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Расход #${state.expenseId}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(state.conflictDateStr, color = Color.Gray, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(state.title, color = Color.DarkGray, fontSize = 14.sp)
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
                    .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Предложенные изменения",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF9333EA),
                    )
                    Text(
                        "От: ${state.remoteUserName}",
                        fontSize = 12.sp,
                        color = Color(0xFF9333EA).copy(alpha = 0.8f),
                    )
                }
                Box(
                    modifier = Modifier.size(36.dp).background(Color(0xFF9333EA), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.remoteUserName.firstOrNull()?.toString() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val sumChanged = state.sum.baseValue != state.sum.remoteValue
            val catChanged = state.category.baseValue != state.category.remoteValue
            val descChanged = state.description.baseValue != state.description.remoteValue
            val payerChanged = state.payer.baseValue != state.payer.remoteValue
            val splitChanged = state.split.baseValue != state.split.remoteValue

            ConflictRow("Сумма:", state.sum.baseValue, state.sum.remoteValue, sumChanged)
            ConflictRow(
                "Категория:",
                state.category.baseValue,
                state.category.remoteValue,
                catChanged,
            )
            ConflictRow(
                "Описание:",
                state.description.baseValue,
                state.description.remoteValue,
                descChanged,
            )
            ConflictRow("Оплатил:", state.payer.baseValue, state.payer.remoteValue, payerChanged)
            ConflictRow("Разделение:", state.split.baseValue, state.split.remoteValue, splitChanged)
        }

        DSButton(
            text = "Принять изменения",
            onClick = onAcceptProposed,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Check,
        )

        DSButton(
            text = "Отклонить (Оставить как было)",
            onClick = onKeepCurrent,
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFFFEE2E2),
            contentColor = Color(0xFF991B1B),
            icon = Icons.Default.Close,
        )

        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Закрыть", color = Color.Gray)
        }
    }
}

@Composable
fun ConflictRow(
    label: String,
    base: String,
    proposed: String,
    isChanged: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = base,
                color = if (isChanged) Color.Gray else Color.Black,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (isChanged) {
                Text("→", color = Color(0xFF9333EA), modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = proposed,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF9333EA),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
