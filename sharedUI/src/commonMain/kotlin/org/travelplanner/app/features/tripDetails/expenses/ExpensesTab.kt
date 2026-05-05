package org.travelplanner.app.features.tripDetails.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.DSEmptyStateCard
import org.travelplanner.app.core.TripUtils.isoToEpochMillis
import org.travelplanner.app.core.formatRussianDateTime
import org.travelplanner.app.domain.Expense
import org.travelplanner.app.features.tripDetails.expenses.details.ExpenseDetailsScreen
import org.travelplanner.app.features.tripDetails.history.ui.ExpenseConflictScreen
import org.travelplanner.app.features.tripDetails.history.ui.ExpenseConflictUi
import org.travelplanner.app.features.tripDetails.history.ui.ExpenseMergePicker
import org.travelplanner.app.features.tripDetails.history.ui.ExpensePendingProposalScreen
import org.travelplanner.app.features.tripDetails.history.ui.MergeRow
import org.travelplanner.app.features.tripDetails.route.ui.getCategoryEmoji
import org.travelplanner.app.theme.DSTextChip
import org.travelplanner.app.theme.DSTextInput
import kotlin.time.Clock

data class ExpensesTab(
    private val tripId: String,
) : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.AccountBalanceWallet)
            return remember { TabOptions(index = 2u, title = "Расходы", icon = icon) }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val parentNavigator = LocalNavigator.currentOrThrow.parent!!
        val listScreenModel =
            parentNavigator.rememberNavigatorScreenModel<ExpensesScreenModel>(tag = tripId) {
                GlobalContext.get().get<ExpensesScreenModel> { parametersOf(tripId) }
            }

        val listState by listScreenModel.state.collectAsState()
        val formScreenModel = getScreenModel<ExpenseFormScreenModel> { parametersOf(tripId) }

        var isAddSheetVisible by remember { mutableStateOf(false) }

        var conflictTarget by remember { mutableStateOf<ConflictTarget?>(null) }
        var mergeTarget by remember { mutableStateOf<ConflictTarget?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        val showSnackbar: (String) -> Unit = { message ->
            scope.launch { snackbarHostState.showSnackbar(message) }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        formScreenModel.handleIntent(ExpenseFormIntent.Initialize(null))
                        isAddSheetVisible = true
                    },
                    containerColor = Color(0xFF155DFC),
                    contentColor = Color.White,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Expense")
                }
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF9FAFB))
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                DSTextInput(
                    value = listState.searchQuery,
                    onValueChange = { listScreenModel.handleIntent(ExpensesIntent.Search(it)) },
                    placeholder = "Поиск расходов...",
                    icon = Icons.Default.Search,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DSTextChip(
                        text = "Все",
                        isActive = listState.activeCategory == "ALL",
                        onClick = { listScreenModel.handleIntent(ExpensesIntent.CategorySelect("ALL")) },
                    )
                    DSTextChip(
                        text = "🏠 Жильё",
                        isActive = listState.activeCategory == "HOUSING",
                        onClick = { listScreenModel.handleIntent(ExpensesIntent.CategorySelect("HOUSING")) },
                    )
                    DSTextChip(
                        text = "🍱 Питание",
                        isActive = listState.activeCategory == "FOOD",
                        onClick = { listScreenModel.handleIntent(ExpensesIntent.CategorySelect("FOOD")) },
                    )
                    DSTextChip(
                        text = "🚇 Транспорт",
                        isActive = listState.activeCategory == "TRANSPORT",
                        onClick = { listScreenModel.handleIntent(ExpensesIntent.CategorySelect("TRANSPORT")) },
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF155DFC), Color(0xFF9810FA)),
                                ),
                            ).padding(20.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Column {
                            Text(
                                "Общие расходы",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                            )
                            Text(
                                "${listState.currency}${(listState.totalAmount.toInt())}",
                                color = Color.White,
                                fontSize = 30.sp,
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Ваша доля",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                            )
                            Text(
                                "${listState.currency}${(listState.userShare.toInt())}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                ExpensesList(
                    expenses = listState.expenses,
                    currency = listState.currency,
                    onExpenseClick = { expense ->
                        val pendingJson = expense.pendingUpdateJson
                        if (pendingJson != null) {
                            val resolved =
                                resolvePayloads(
                                    expense = expense,
                                    pendingJson = pendingJson,
                                    currentUserId = listState.currentUserId,
                                    participants = listState.participants,
                                )
                            if (resolved != null) {
                                conflictTarget =
                                    ConflictTarget(
                                        expense = expense,
                                        payloads = resolved,
                                        ui =
                                            resolved.toUi(
                                                expense = expense,
                                                participants = listState.participants,
                                                currency = listState.currency,
                                                formatIso = ::formatRussianDateTime,
                                            ),
                                        isCreator =
                                            listState.currentUserId != null &&
                                                listState.currentUserId == expense.creatorUserId,
                                    )
                            }
                        } else {
                            parentNavigator.push(ExpenseDetailsScreen(expense.id, tripId))
                        }
                    },
                )
            }

            if (isAddSheetVisible) {
                ModalBottomSheet(
                    onDismissRequest = { isAddSheetVisible = false },
                    containerColor = Color.Transparent,
                    dragHandle = null,
                ) {
                    ExpenseFormSheet(
                        screenModel = formScreenModel,
                        onDismiss = { isAddSheetVisible = false },
                        onSuccess = { message ->
                            isAddSheetVisible = false
                            if (message != null) showSnackbar(message)
                        },
                    )
                }
            }

            val activeConflict = conflictTarget
            if (activeConflict != null) {
                ModalBottomSheet(
                    onDismissRequest = { conflictTarget = null },
                    containerColor = Color.Transparent,
                    dragHandle = null,
                ) {
                    val expenseRemoteId = activeConflict.expense.id

                    if (activeConflict.isCreator) {
                        ExpenseConflictScreen(
                            ui = activeConflict.ui,
                            onSaveMine = {
                                val accept = activeConflict.payloads.mineIsProposer
                                listScreenModel.handleIntent(
                                    ExpensesIntent.ResolveConflict(
                                        tripId,
                                        expenseRemoteId,
                                        accept = accept,
                                    ),
                                )
                                conflictTarget = null
                            },
                            onSaveTheirs = {
                                val accept = !activeConflict.payloads.mineIsProposer
                                listScreenModel.handleIntent(
                                    ExpensesIntent.ResolveConflict(
                                        tripId,
                                        expenseRemoteId,
                                        accept = accept,
                                    ),
                                )
                                conflictTarget = null
                            },
                            onMerge = {
                                mergeTarget = activeConflict
                                conflictTarget = null
                            },
                            onRevert = {
                                listScreenModel.handleIntent(
                                    ExpensesIntent.RevertConflict(tripId, expenseRemoteId),
                                )
                                conflictTarget = null
                            },
                            onCancel = { conflictTarget = null },
                        )
                    } else {
                        ExpensePendingProposalScreen(
                            ui = activeConflict.ui,
                            onCancel = { conflictTarget = null },
                        )
                    }
                }
            }

            val activeMerge = mergeTarget
            if (activeMerge != null) {
                ModalBottomSheet(
                    onDismissRequest = { mergeTarget = null },
                    containerColor = Color.Transparent,
                    dragHandle = null,
                ) {
                    val rows: List<MergeRow> =
                        activeMerge.payloads.toMergeRows(
                            participants = listState.participants,
                            currency = listState.currency,
                        )
                    val expenseRemoteId = activeMerge.expense.id
                    ExpenseMergePicker(
                        rows = rows,
                        onApply = { choices ->
                            if (!activeMerge.isCreator) {
                                showSnackbar("Только создатель расхода может принять решение")
                                mergeTarget = null
                                return@ExpenseMergePicker
                            }
                            listScreenModel.handleIntent(
                                ExpensesIntent.MergeConflict(
                                    tripId = tripId,
                                    expenseRemoteId = expenseRemoteId,
                                    merged = activeMerge.payloads.toMergeRequest(choices),
                                ),
                            )
                            mergeTarget = null
                        },
                        onCancel = { mergeTarget = null },
                    )
                }
            }
        }
    }
}

private data class ConflictTarget(
    val expense: Expense,
    val payloads: ResolvedPayloads,
    val ui: ExpenseConflictUi,
    val isCreator: Boolean,
)

@Composable
fun ExpensesList(
    expenses: List<Expense>,
    currency: String,
    onExpenseClick: (Expense) -> Unit,
) {
    if (expenses.isEmpty()) {
        DSEmptyStateCard(
            title = "Нет расходов",
            description = "Добавьте первый расход, чтобы начать отслеживание бюджета",
            buttonText = "Добавить расход",
            onButtonClick = {},
            icon = Icons.Default.Receipt,
        )
        return
    }

    val grouped =
        expenses.groupBy {
            val dateMillis = isoToEpochMillis(it.date)
            if (Clock.System.now().toEpochMilliseconds() - dateMillis < 86400000L) {
                "Сегодня"
            } else {
                "Вчера"
            }
        }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        grouped.forEach { (header, items) ->
            item {
                Text(
                    header,
                    fontSize = 12.sp,
                    color = Color(0xFF6A7282),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            items(items, key = { it.id }) { expense ->
                ExpenseCard(expense, currency, onClick = { onExpenseClick(expense) })
            }
        }
    }
}

@Composable
fun ExpenseCard(
    expense: Expense,
    currency: String,
    onClick: () -> Unit,
) {
    val hasConflict = expense.pendingUpdateJson != null

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (hasConflict) Color(0xFFFFF7ED) else Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .border(
                    width = if (hasConflict) 1.dp else 0.dp,
                    color = if (hasConflict) Color(0xFFF97316) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(getCategoryEmoji(expense.category), fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(expense.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            if (hasConflict) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.WarningAmber,
                                    null,
                                    tint = Color(0xFFEA580C),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(
                            if (hasConflict) "Ожидает подтверждения" else getCategoryName(expense.category),
                            fontSize = 14.sp,
                            color = if (hasConflict) Color(0xFFEA580C) else Color(0xFF6A7282),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${currency}${(expense.amount)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        expense.splitDescription,
                        fontSize = 12.sp,
                        color = Color(0xFF6A7282),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Divider(color = Color(0xFFF3F4F6))
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF6A7282),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Оплатил: ${expense.payerName}",
                        fontSize = 12.sp,
                        color = Color(0xFF6A7282),
                    )
                }
                Icon(
                    Icons.Default.Receipt,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF99A1AF),
                )
            }
        }
    }
}

fun getCategoryName(cat: String) =
    when (cat) {
        "HOUSING" -> "Жильё"
        "FOOD" -> "Питание"
        "TRANSPORT" -> "Транспорт"
        "FUN" -> "Развлечения"
        else -> "Прочее"
    }

fun getCategoryEmoji(cat: String) =
    when (cat) {
        "HOUSING" -> "🏠"
        "FOOD" -> "🍱"
        "TRANSPORT" -> "🚇"
        "FUN" -> "🎭"
        else -> "🧾"
    }
