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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MergeType
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
import org.travelplanner.app.features.profile.ui.GradientAvatar
import org.travelplanner.app.features.profile.ui.avatarInitials
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSNotificationBanner

data class ExpenseSideSnapshot(
    val amountFormatted: String,
    val category: String,
    val description: String,
    val payerName: String,
    val splitsText: String,
    val receiptUrl: String?,
    val modifiedAt: String,
    val modifiedByUserId: String,
    val modifiedByName: String,
)

data class ExpenseBaseSnapshot(
    val amountFormatted: String,
    val category: String,
    val description: String,
    val payerName: String,
    val splitsText: String,
    val receiptUrl: String?,
)

data class ExpenseConflictUi(
    val expenseShortId: String,
    val title: String,
    val conflictAtFormatted: String,
    val mine: ExpenseSideSnapshot,
    val theirs: ExpenseSideSnapshot,
    val base: ExpenseBaseSnapshot?,
)

private val BlueAccent = Color(0xFF155DFC)
private val BlueAccentBg = Color(0xFFEFF6FF)
private val PurpleAccent = Color(0xFF9333EA)
private val PurpleAccentBg = Color(0xFFFAF5FF)
private val OrangeBg = Color(0xFFFFF7ED)
private val OrangeBorder = Color(0xFFFFEDD5)
private val OrangeText = Color(0xFF9A3412)
private val OrangeIcon = Color(0xFFC2410C)
private val RedBg = Color(0xFFFEE2E2)
private val RedText = Color(0xFF991B1B)
private val SubtleText = Color(0xFF6B7280)
private val InfoBg = Color(0xFFF3F4F6)

@Composable
fun ExpenseConflictScreen(
    ui: ExpenseConflictUi,
    onSaveMine: () -> Unit,
    onSaveTheirs: () -> Unit,
    onMerge: () -> Unit,
    onRevert: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFFAFAFA), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DSNotificationBanner(
            title = "Обнаружен конфликт",
            text = "Расход был изменён вами и другим участником одновременно. Выберите, какую версию сохранить.",
            backgroundColor = OrangeBg,
            borderColor = OrangeBorder,
            contentColor = OrangeText,
            iconColor = OrangeIcon,
            icon = Icons.Default.WarningAmber,
        )

        ExpenseHeaderCard(
            shortId = ui.expenseShortId,
            title = ui.title,
            conflictAt = ui.conflictAtFormatted,
        )

        VersionCard(
            label = "Ваша версия",
            modifiedSubtitle = "Изменено ${ui.mine.modifiedAt}",
            accent = BlueAccent,
            accentBg = BlueAccentBg,
            snapshot = ui.mine,
            base = ui.base,
            saveButtonText = "Сохранить эту версию",
            onSave = onSaveMine,
        )

        VersionCard(
            label = "Версия сервера",
            modifiedSubtitle = "Изменено ${ui.theirs.modifiedByName}, ${ui.theirs.modifiedAt}",
            accent = PurpleAccent,
            accentBg = PurpleAccentBg,
            snapshot = ui.theirs,
            base = ui.base,
            saveButtonText = "Сохранить эту версию",
            onSave = onSaveTheirs,
        )

        DSButton(
            text = "Объединить изменения",
            onClick = onMerge,
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = InfoBg,
            contentColor = Color(0xFF111827),
            icon = Icons.Default.MergeType,
        )

        DSButton(
            text = "Отменить оба изменения",
            onClick = onRevert,
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = RedBg,
            contentColor = RedText,
            icon = Icons.Default.Close,
        )

        TipFooter()

        TextButton(
            onClick = onCancel,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("Закрыть", color = SubtleText)
        }
    }
}

@Composable
private fun ExpenseHeaderCard(
    shortId: String,
    title: String,
    conflictAt: String,
) {
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
            Text("Расход #$shortId", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Конфликт от $conflictAt", color = SubtleText, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(title, color = Color.DarkGray, fontSize = 14.sp)
    }
}

@Composable
private fun VersionCard(
    label: String,
    modifiedSubtitle: String,
    accent: Color,
    accentBg: Color,
    snapshot: ExpenseSideSnapshot,
    base: ExpenseBaseSnapshot?,
    saveButtonText: String,
    onSave: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent)
                Text(modifiedSubtitle, fontSize = 12.sp, color = accent.copy(alpha = 0.8f))
            }
            GradientAvatar(
                seed = snapshot.modifiedByUserId + snapshot.modifiedByName,
                initials = avatarInitials(snapshot.modifiedByName),
                avatarUrl = null,
                size = 36.dp,
                fontSize = 14.sp,
                showBorder = false,
            )
        }

        InfoRow("Сумма:", snapshot.amountFormatted, valueBold = true)
        InfoRow("Категория:", snapshot.category)
        InfoRow("Описание:", snapshot.description, valueBold = true)
        InfoRow("Оплатил:", snapshot.payerName, valueBold = true)
        InfoRow("Разделить:", snapshot.splitsText)

        val diff = base?.let { buildDiffLines(it, snapshot) }.orEmpty()
        if (diff.isNotEmpty()) {
            DiffSummaryChip(accent = accent, accentBg = accentBg, lines = diff)
        }

        DSButton(
            text = saveButtonText,
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = accent,
            contentColor = Color.White,
            icon = Icons.Default.Check,
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueBold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = SubtleText, fontSize = 14.sp)
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = if (valueBold) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun DiffSummaryChip(
    accent: Color,
    accentBg: Color,
    lines: List<String>,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(accentBg, RoundedCornerShape(12.dp))
                .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(20.dp)
                    .background(accent, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.size(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Изменения:", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            lines.forEach {
                Text(it, color = accent.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TipFooter() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFFEF9C3), RoundedCornerShape(12.dp))
                .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("💡", fontSize = 18.sp)
        Spacer(Modifier.size(8.dp))
        Column {
            Text(
                "Совет:",
                color = Color(0xFF854D0E),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                "При объединении изменений будет создана новая версия с учётом обеих правок. " +
                    "Рекомендуется связаться с другим участником для уточнения деталей.",
                color = Color(0xFF854D0E),
                fontSize = 13.sp,
            )
        }
    }
}

private fun buildDiffLines(
    base: ExpenseBaseSnapshot,
    side: ExpenseSideSnapshot,
): List<String> =
    buildList {
        if (base.amountFormatted != side.amountFormatted) {
            add("Сумма: ${base.amountFormatted} → ${side.amountFormatted}")
        }
        if (base.category != side.category) {
            add("Категория: ${base.category} → ${side.category}")
        }
        if (base.description != side.description) {
            add("Описание: «${base.description}» → «${side.description}»")
        }
        if (base.payerName != side.payerName) {
            add("Плательщик: ${base.payerName} → ${side.payerName}")
        }
        if (base.splitsText != side.splitsText) {
            add("Разделение изменено")
        }
        if ((base.receiptUrl ?: "") != (side.receiptUrl ?: "")) {
            if (base.receiptUrl == null && side.receiptUrl != null) {
                add("Добавлен чек")
            } else if (base.receiptUrl != null && side.receiptUrl == null) {
                add("Чек удалён")
            } else {
                add("Чек обновлён")
            }
        }
    }
