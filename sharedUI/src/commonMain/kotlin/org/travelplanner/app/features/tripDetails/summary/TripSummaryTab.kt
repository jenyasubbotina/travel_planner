package org.travelplanner.app.features.tripDetails.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.koin.koinNavigatorScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.core.TripUtils.toReadableDate
import org.travelplanner.app.features.tripDetails.EditBudgetDialog
import org.travelplanner.app.features.tripDetails.expenses.ExpensesTab
import org.travelplanner.app.features.tripDetails.more.MoreTab
import org.travelplanner.app.features.tripDetails.more.parseColor
import org.travelplanner.app.features.tripDetails.route.ui.ItineraryTab
import org.travelplanner.app.theme.DSNotificationBanner
import org.travelplanner.app.theme.DSProgressBar
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Clock

data class TripSummaryTab(
    private val tripId: Long,
) : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Home)
            return remember { TabOptions(index = 0u, title = "Сводка", icon = icon) }
        }

    @Composable
    override fun Content() {
        val parentNavigator = LocalNavigator.currentOrThrow.parent!!
        val screenModel =
            parentNavigator.koinNavigatorScreenModel<TripSummaryScreenModel> { parametersOf(tripId) }

        val state by screenModel.state.collectAsState()

        val tabNavigator = LocalTabNavigator.current

        var showBudgetDialog by remember { mutableStateOf(false) }

        TripSummaryContent(
            state = state,
            onAddExpenseClick = {
                tabNavigator.current = ExpensesTab(tripId)
            },
            onAddEventClick = {
                tabNavigator.current = ItineraryTab(tripId)
            },
            onInviteClick = {
                tabNavigator.current = MoreTab(tripId)
            },
            onViewAllExpensesClick = {
                tabNavigator.current = ExpensesTab(tripId)
            },
            onViewItineraryClick = {
                tabNavigator.current = ItineraryTab(tripId)
            },
            onChangeBudgetClick = {
                showBudgetDialog = true
            },
        )
        if (showBudgetDialog && state.trip != null) {
            EditBudgetDialog(
                currentBudget = state.trip!!.totalBudget,
                onDismiss = { showBudgetDialog = false },
                onConfirm = { newBudget ->
                    screenModel.handleIntent(TripSummaryIntent.UpdateBudget(newBudget))
                    showBudgetDialog = false
                },
            )
        }
    }
}

@Composable
fun TripSummaryContent(
    state: TripSummaryState,
    onAddExpenseClick: () -> Unit,
    onAddEventClick: () -> Unit,
    onInviteClick: () -> Unit,
    onViewAllExpensesClick: () -> Unit,
    onViewItineraryClick: () -> Unit,
    onChangeBudgetClick: () -> Unit,
) {
    val t = state.trip ?: return
    val participants = state.participants
    val expenses = state.expenses
    val events = state.events

    val totalSpent = expenses.filter { it.category != "PAYMENT" }.sumOf { it.amount }
    val remainingBudget = t.totalBudget - totalSpent
    val progress =
        if (t.totalBudget > 0) (totalSpent / t.totalBudget).toFloat().coerceIn(0f, 1f) else 0f

    val currentMillis = Clock.System.now().toEpochMilliseconds()
    val msPerDay = 1000.0 * 60 * 60 * 24

    val tripTotalDays = maxOf(1, ceil((t.endDate - t.startDate) / msPerDay).toInt())

    val daysSpent =
        when {
            currentMillis < t.startDate -> 0
            currentMillis > t.endDate -> tripTotalDays
            else -> maxOf(1, ceil((currentMillis - t.startDate) / msPerDay).toInt())
        }

    val actualAvgPerDay = if (daysSpent > 0) totalSpent / daysSpent else 0.0

    val daysToStart = ceil((t.startDate - currentMillis) / msPerDay).toInt()

    val statusText =
        when {
            currentMillis < t.startDate -> if (daysToStart == 1) "Завтра" else "Через $daysToStart дн."
            currentMillis > t.endDate -> "Завершена"
            else -> "В поездке"
        }

    val forecastText =
        when {
            remainingBudget < 0 -> {
                "Бюджет превышен на ${t.currency}${(-remainingBudget).toInt()}!"
            }

            currentMillis > t.endDate -> {
                "Завершена • В среднем вы тратили ${t.currency}${actualAvgPerDay.toInt()} в день"
            }

            totalSpent == 0.0 -> {
                val targetDaily = t.totalBudget / tripTotalDays
                "План трат: ${t.currency}${targetDaily.toInt()} в день"
            }

            else -> {
                val daysLeftInTrip = tripTotalDays - daysSpent
                val projectedTotal = totalSpent + (actualAvgPerDay * daysLeftInTrip)

                if (projectedTotal <= t.totalBudget) {
                    "Траты ${t.currency}${actualAvgPerDay.toInt()}/день • Прогноз в норме"
                } else {
                    val daysBudgetWillLast = floor(remainingBudget / actualAvgPerDay).toInt()

                    if (daysBudgetWillLast == 0) {
                        "Траты ${t.currency}${actualAvgPerDay.toInt()}/день • Денег не хватит до конца дня!"
                    } else {
                        "Траты ${t.currency}${actualAvgPerDay.toInt()}/день • Бюджета хватит на $daysBudgetWillLast дн."
                    }
                }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF9FAFB))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush =
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF155DFC), Color(0xFF9810FA)),
                            ),
                    ).padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(t.destination, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                }

                Text(t.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "${t.startDate.toReadableDate()} — ${t.endDate.toReadableDate()}",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        statusText,
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }

                Text("Участники (${participants.size})", color = Color.White, fontSize = 14.sp)

                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    participants.take(5).forEach { person ->
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .background(
                                        brush =
                                            Brush.linearGradient(
                                                colors =
                                                    listOf(
                                                        Color(parseColor(person.avatarColor1)),
                                                        Color(parseColor(person.avatarColor2)),
                                                    ),
                                            ),
                                        shape = CircleShape,
                                    ).border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            val initials =
                                person.name
                                    .split(" ")
                                    .mapNotNull { it.firstOrNull() }
                                    .joinToString("")
                                    .take(2)
                                    .uppercase()
                            Text(
                                initials,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.3f), CircleShape)
                                .border(1.5.dp, Color.White, CircleShape)
                                .clip(CircleShape)
                                .clickable { onInviteClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Бюджет", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "Изменить",
                        fontSize = 14.sp,
                        color = Color(0xFF155DFC),
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onChangeBudgetClick() }
                                .padding(horizontal = 4.dp),
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Text(
                            "${t.currency}${totalSpent.toInt()}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Normal,
                        )
                        Text(
                            "из ${t.currency}${t.totalBudget.toInt()}",
                            fontSize = 14.sp,
                            color = Color(0xFF6A7282),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Осталось", fontSize = 14.sp, color = Color(0xFF6A7282))
                        Text(
                            "${t.currency}${remainingBudget.toInt()}",
                            fontSize = 20.sp,
                            color =
                                if (remainingBudget >= 0) {
                                    Color(0xFF00A63E)
                                } else {
                                    Color(
                                        0xFFDC2626,
                                    )
                                },
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF3F4F6)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        colors =
                                            if (remainingBudget >= 0) {
                                                listOf(Color(0xFF00C950), Color(0xFF2B7FFF))
                                            } else {
                                                listOf(
                                                    Color(0xFFFCA5A5),
                                                    Color(0xFFDC2626),
                                                )
                                            },
                                    ),
                                ),
                    )
                }

                Text(
                    text = forecastText,
                    fontSize = 12.sp,
                    color =
                        if (remainingBudget >= 0 && !forecastText.contains("хватит на")) {
                            Color(
                                0xFF6A7282,
                            )
                        } else {
                            Color(0xFFDC2626)
                        },
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(
                icon = "💲",
                text = "Добавить\nрасход",
                bgColor = Color(0xFFDBEAFE),
                iconColor = Color(0xFF155DFC),
                modifier = Modifier.weight(1f),
                onClick = onAddExpenseClick,
            )
            ActionButton(
                icon = "📍",
                text = "Добавить\nточку",
                bgColor = Color(0xFFF3E8FF),
                iconColor = Color(0xFF9810FA),
                modifier = Modifier.weight(1f),
                onClick = onAddEventClick,
            )
            ActionButton(
                icon = "👥",
                text = "Пригласить",
                bgColor = Color(0xFFDCFCE7),
                iconColor = Color(0xFF00A63E),
                modifier = Modifier.weight(1f),
                onClick = onInviteClick,
            )
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Расходы по категориям", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "Все",
                        fontSize = 14.sp,
                        color = Color(0xFF155DFC),
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onViewAllExpensesClick() }
                                .padding(horizontal = 4.dp),
                    )
                }

                val expensesByCategory =
                    expenses
                        .filter { it.category != "PAYMENT" }
                        .groupBy { it.category }
                        .mapValues { entry -> entry.value.sumOf { it.amount } }
                        .entries
                        .sortedByDescending { it.value }
                        .take(4)

                if (expensesByCategory.isEmpty()) {
                    Text("Пока нет расходов", fontSize = 14.sp, color = Color(0xFF6A7282))
                } else {
                    expensesByCategory.forEach { (category, amount) ->
                        val (emoji, name, color) = getCategoryUiData(category)
                        val catProgress =
                            if (totalSpent > 0) (amount / totalSpent).toFloat() else 0f
                        CategoryRow(
                            emoji,
                            name,
                            "${t.currency}${amount.toInt()}",
                            color,
                            catProgress,
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ближайшие события", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = "Маршрут",
                        fontSize = 14.sp,
                        color = Color(0xFF155DFC),
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onViewItineraryClick() }
                                .padding(horizontal = 4.dp),
                    )
                }

                val displayEvents =
                    events
                        .sortedWith(compareBy({ it.dayIndex }, { it.time }))
                        .take(3)

                if (displayEvents.isEmpty()) {
                    Text(
                        "Маршрут пока не запланирован",
                        fontSize = 14.sp,
                        color = Color(0xFF6A7282),
                    )
                } else {
                    displayEvents.forEachIndexed { index, event ->
                        val dotColor =
                            if (index == 0) Color(0xFF00C950) else null
                        EventRow(
                            event.time,
                            event.title,
                            event.subtitle,
                            dotColor,
                        )
                    }
                }
            }
        }

        if (remainingBudget < 0) {
            DSNotificationBanner(
                title = "Внимание",
                text = "Вы превысили запланированный бюджет на ${t.currency}${(-remainingBudget).toInt()}",
                backgroundColor = Color(0xFFFEF2F2),
                borderColor = Color(0xFFFECACA),
                contentColor = Color(0xFF991B1B),
                iconColor = Color(0xFF991B1B),
                icon = Icons.Default.WarningAmber,
            )
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun ActionButton(
    icon: String,
    text: String,
    bgColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .height(128.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .clickable { onClick() }
                .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .background(bgColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 24.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(text, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
    }
}

@Composable
fun CategoryRow(
    emoji: String,
    name: String,
    amount: String,
    color: Color,
    progress: Float,
) {
    DSProgressBar(
        label = "$emoji $name",
        valueDisplay = amount,
        progress = progress,
        color = color,
    )
}

@Composable
fun EventRow(
    time: String,
    title: String,
    subtitle: String,
    dotColor: Color?,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            time,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(40.dp),
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF6A7282),
                    modifier = Modifier.size(12.dp),
                )
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF6A7282))
            }
            if (dotColor != null) {
                Box(Modifier.padding(top = 4.dp).size(8.dp).background(dotColor, CircleShape))
            }
        }
    }
}
