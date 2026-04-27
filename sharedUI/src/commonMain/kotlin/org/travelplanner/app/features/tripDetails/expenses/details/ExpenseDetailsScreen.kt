package org.travelplanner.app.features.tripDetails.expenses.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.core.TripUtils.formatDateIso
import org.travelplanner.app.core.rememberFileDownloader
import org.travelplanner.app.core.rememberResolvedImageUrl
import org.travelplanner.app.features.tripDetails.expenses.ExpenseFormIntent
import org.travelplanner.app.features.tripDetails.expenses.ExpenseFormScreenModel
import org.travelplanner.app.features.tripDetails.expenses.ExpenseFormSheet
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.features.profile.ui.GradientAvatar
import org.travelplanner.app.features.profile.ui.avatarInitials
import org.travelplanner.app.features.tripDetails.expenses.getCategoryEmoji
import org.travelplanner.app.features.tripDetails.expenses.getCategoryName
import org.travelplanner.app.theme.DSLoadingOverlay

data class ExpenseDetailsScreen(
    val expenseId: Long,
    val tripId: String,
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val detailsModel =
            getScreenModel<ExpenseDetailsScreenModel> { parametersOf(expenseId, tripId) }
        val detailsState by detailsModel.state.collectAsState()

        val formModel = getScreenModel<ExpenseFormScreenModel> { parametersOf(tripId) }
        var isEditSheetVisible by remember { mutableStateOf(false) }

        LaunchedEffect(detailsState) {
            if (detailsState is ExpenseDetailsUiState.Deleted) {
                navigator.pop()
            }
        }

        Scaffold(
            containerColor = Color(0xFFF9FAFB),
            bottomBar = {
                if (detailsState is ExpenseDetailsUiState.Success) {
                    ActionsBar(
                        onEdit = {
                            formModel.handleIntent(ExpenseFormIntent.Initialize(expenseId))
                            isEditSheetVisible = true
                        },
                        onDelete = { detailsModel.handleIntent(ExpenseDetailsIntent.DeleteExpense) },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (val uiState = detailsState) {
                    is ExpenseDetailsUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            DSLoadingOverlay()
                        }
                    }

                    is ExpenseDetailsUiState.Error -> {
                        Text("Расход не найден")
                    }

                    is ExpenseDetailsUiState.Success -> {
                        ExpenseDetailsContent(uiState.details)
                    }

                    else -> {}
                }
            }
        }

        if (isEditSheetVisible) {
            ModalBottomSheet(
                onDismissRequest = { isEditSheetVisible = false },
                containerColor = Color.Transparent,
                dragHandle = null,
            ) {
                ExpenseFormSheet(
                    screenModel = formModel,
                    onDismiss = { isEditSheetVisible = false },
                    onSuccess = { isEditSheetVisible = false },
                )
            }
        }
    }
}

@Composable
fun ExpenseDetailsContent(
    details: ExpenseFullDetails,
) {
    val expense = details.expense
    val downloadFile = rememberFileDownloader()
    var showDownloadDialog by remember { mutableStateOf(false) }
    val resolvedReceiptUrl = rememberResolvedImageUrl(expense.imageUrl)

    if (showDownloadDialog && expense.imageUrl != null) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Скачать чек?") },
            text = { Text("Файл будет сохранён в папку загрузок.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDownloadDialog = false
                        val url = resolvedReceiptUrl ?: return@Button
                        val ext = expense.imageUrl!!.substringAfterLast(".", "jpg")
                        downloadFile(url, "receipt_${expense.id}.$ext")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF155DFC)),
                ) {
                    Text("Скачать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) { Text("Отмена") }
            },
        )
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = getCategoryEmoji(expense.category), fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = expense.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0A0A0A),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = getCategoryName(expense.category),
                        fontSize = 14.sp,
                        color = Color(0xFF6A7282),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "${details.currency}${
                            (expense.amount.toDoubleOrNull()?.toInt() ?: 0).toString().reversed().chunked(3)
                                .joinToString(" ").reversed()
                        }",
                        fontSize = 36.sp,
                        color = Color(0xFF0A0A0A),
                    )
                    Spacer(Modifier.height(8.dp))
                    val denom = if (details.splits.isNotEmpty()) details.splits.size else 1
                    val perPerson = (expense.amount.toDoubleOrNull()?.toInt() ?: 0) / denom

                    Text(
                        text = "$perPerson per person",
                        fontSize = 14.sp,
                        color = Color(0xFF6A7282),
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Информация", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(16.dp))

                    InfoRow(
                        label = "Оплатил",
                        value = expense.payerName,
                        icon = Icons.Default.Person,
                    )
                    Spacer(Modifier.height(12.dp))
                    InfoRow(
                        label = "Дата и время",
                        value = formatDateIso(details.expense.date),
                        icon = Icons.Default.CalendarToday,
                    )
                }
            }
        }

        item {
            RealSplitCard(details)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Чек", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(16.dp))

                    if (expense.imageUrl != null) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 500.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF3F4F6)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = resolvedReceiptUrl,
                                contentDescription = "Receipt",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(Color(0xFFF3F4F6), RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Receipt,
                                    contentDescription = null,
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Фото чека", color = Color(0xFF9CA3AF), fontSize = 14.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { showDownloadDialog = true },
                        enabled = expense.imageUrl != null,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF3F4F6),
                                contentColor = Color(0xFF0A0A0A),
                            ),
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Скачать", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        item {
            RealHistoryCard(details.history)
        }
    }
}

@Composable
fun RealSplitCard(details: ExpenseFullDetails) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Разделение расходов", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))

            details.splits.forEach { split ->
                val participant = details.participants.find { it.userId == split.participantId }
                if (participant != null) {
                    val isPaid = split.isPaid
                    val statusText = if (isPaid) "✓ Оплачено" else "Ожидает оплаты"
                    val amountColor = if (isPaid) Color(0xFF00A63E) else Color(0xFFF54900)

                    SplitRow(
                        participant = participant,
                        status = statusText,
                        amount = "${details.currency}${split.amount.toDoubleOrNull()?.toInt() ?: 0}",
                        amountColor = amountColor,
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun RealHistoryCard(history: List<ExpenseHistoryUiModel>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("История изменений", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))

            if (history.isEmpty()) {
                Text("Пока нет изменений", color = Color(0xFF6A7282), fontSize = 14.sp)
            } else {
                history.forEach { item ->
                    HistoryItem(
                        text = item.title,
                        subtext = item.subtitle,
                        isLast = item.isLast,
                    )
                }
            }
        }
    }
}

@Composable
fun ActionsBar(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onEdit,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF155DFC)),
        ) {
            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Редактировать", fontSize = 16.sp)
        }

        Button(
            onClick = onDelete,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFEF2F2),
                    contentColor = Color(0xFFE7000B),
                ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .background(Color(0xFFF3F4F6), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = Color(0xFF4A5565))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 14.sp, color = Color(0xFF6A7282))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0A0A0A))
        }
    }
}

@Composable
fun SplitRow(
    participant: Participant,
    status: String,
    amount: String,
    amountColor: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF9FAFB), RoundedCornerShape(16.dp))
                .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradientAvatar(
                seed = participant.userId + participant.name,
                initials = avatarInitials(participant.name),
                avatarUrl = participant.avatarUrl,
                size = 40.dp,
                fontSize = 16.sp,
                showBorder = false,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(participant.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(status, fontSize = 12.sp, color = Color(0xFF6A7282))
            }
        }
        Text(amount, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = amountColor)
    }
}

@Composable
fun HistoryItem(
    text: String,
    subtext: String,
    isLast: Boolean,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(20.dp),
        ) {
            Box(Modifier.size(8.dp).background(Color(0xFF155DFC), CircleShape))
            if (!isLast) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFFE5E7EB)))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.padding(bottom = if (isLast) 0.dp else 24.dp)) {
            Text(text, fontSize = 14.sp, color = Color(0xFF0A0A0A))
            Text(subtext, fontSize = 12.sp, color = Color(0xFF6A7282))
        }
    }
}
