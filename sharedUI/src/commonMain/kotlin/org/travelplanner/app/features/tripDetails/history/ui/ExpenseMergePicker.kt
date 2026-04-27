package org.travelplanner.app.features.tripDetails.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.travelplanner.app.theme.DSButton

enum class MergeField { AMOUNT, CATEGORY, DESCRIPTION, PAYER, SPLITS }

enum class MergeSide { MINE, THEIRS }

data class MergeRow(
    val field: MergeField,
    val label: String,
    val mineDisplay: String,
    val theirsDisplay: String,
    val sameOnBothSides: Boolean,
)

@Composable
fun ExpenseMergePicker(
    rows: List<MergeRow>,
    initialChoices: Map<MergeField, MergeSide> = emptyMap(),
    onApply: (Map<MergeField, MergeSide>) -> Unit,
    onCancel: () -> Unit,
) {
    val choices = remember {
        mutableStateMapOf<MergeField, MergeSide>().apply {
            rows.forEach { row ->
                if (!row.sameOnBothSides) {
                    put(row.field, initialChoices[row.field] ?: MergeSide.MINE)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Объединить изменения",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Text(
            "Для каждого поля выберите, чья версия попадёт в итоговый расход.",
            fontSize = 13.sp,
            color = Color(0xFF6B7280),
        )

        rows.filter { !it.sameOnBothSides }.forEach { row ->
            MergeRowCard(
                row = row,
                selected = choices[row.field] ?: MergeSide.MINE,
                onSelect = { choices[row.field] = it },
            )
        }

        Spacer(Modifier.height(4.dp))

        DSButton(
            text = "Применить",
            onClick = { onApply(choices.toMap()) },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.Check,
        )

        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Отмена", color = Color(0xFF6B7280))
        }
    }
}

@Composable
private fun MergeRowCard(
    row: MergeRow,
    selected: MergeSide,
    onSelect: (MergeSide) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(row.label, fontSize = 12.sp, color = Color(0xFF6B7280))
        SideOption(
            label = "Ваша версия",
            value = row.mineDisplay,
            selected = selected == MergeSide.MINE,
            accent = Color(0xFF155DFC),
            onClick = { onSelect(MergeSide.MINE) },
        )
        SideOption(
            label = "Версия сервера",
            value = row.theirsDisplay,
            selected = selected == MergeSide.THEIRS,
            accent = Color(0xFF9333EA),
            onClick = { onSelect(MergeSide.THEIRS) },
        )
    }
}

@Composable
private fun SideOption(
    label: String,
    value: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .border(
                1.dp,
                if (selected) accent else Color(0xFFE5E7EB),
                RoundedCornerShape(10.dp),
            )
            .background(if (selected) accent.copy(alpha = 0.06f) else Color.White, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected = selected, accent = accent)
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = if (selected) accent else Color(0xFF6B7280))
            Text(
                value.ifBlank { "—" },
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean, accent: Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(18.dp)
            .border(2.dp, if (selected) accent else Color(0xFFD1D5DB), CircleShape)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accent, CircleShape),
            )
        }
    }
}

