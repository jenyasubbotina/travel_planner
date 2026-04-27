package org.travelplanner.app.features.tripDetails.route.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.DSEmptyStateCard
import org.travelplanner.app.core.TripUtils.toReadableDateRu
import org.travelplanner.app.domain.Event
import org.travelplanner.app.features.tripDetails.route.detailed.ui.EventDetailsScreen
import org.travelplanner.app.theme.DSButton

data class ItineraryTab(
    private val tripId: String,
) : Tab {
    override val options: TabOptions
        @Composable get() {
            val icon = rememberVectorPainter(Icons.Default.Map)
            return remember { TabOptions(index = 1u, title = "Маршрут", icon = icon) }
        }

    @Composable
    override fun Content() {
        val parentNavigator = LocalNavigator.currentOrThrow.parent!!
        val screenModel =
            parentNavigator.rememberNavigatorScreenModel<ItineraryScreenModel>(tag = tripId) {
                GlobalContext.get().get<ItineraryScreenModel> { parametersOf(tripId) }
            }

        val state by screenModel.state.collectAsState()

        val tabNavigator = LocalNavigator.currentOrThrow

        val mainNavigator = tabNavigator.parent

        Scaffold(
            containerColor = Color(0xFFF9FAFB),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { screenModel.handleIntent(ItineraryIntent.CreateNewEvent) },
                    containerColor = Color(0xFF155DFC),
                    contentColor = Color.White,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить место")
                }
            },
        ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding).background(Color(0xFFF9FAFB)),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF9FAFB))
                        .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                            .padding(4.dp),
                ) {
                    Row(Modifier.fillMaxSize()) {
                        ViewModeButton(
                            text = "Список",
                            icon = Icons.Default.FormatListBulleted,
                            isSelected = state.viewMode == ItineraryViewMode.LIST,
                            modifier = Modifier.weight(1f),
                        ) { screenModel.handleIntent(ItineraryIntent.SetViewMode(ItineraryViewMode.LIST)) }

                        ViewModeButton(
                            text = "Карта",
                            icon = Icons.Default.Map,
                            isSelected = state.viewMode == ItineraryViewMode.MAP,
                            modifier = Modifier.weight(1f),
                        ) { screenModel.handleIntent(ItineraryIntent.SetViewMode(ItineraryViewMode.MAP)) }
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.dayCount) { index ->
                        DayChip(
                            dayIndex = index,
                            startDate = state.tripStartDate,
                            count = state.eventsCountByDay[index] ?: 0,
                            isSelected = state.selectedDayIndex == index,
                            onClick = { screenModel.handleIntent(ItineraryIntent.SelectDay(index)) },
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (state.viewMode == ItineraryViewMode.LIST) {
                    ItineraryListView(
                        events = state.events,
                        currency = state.currency,
                        onEdit = { screenModel.handleIntent(ItineraryIntent.EditEvent(it)) },
                        onMapClick = {
                            screenModel.handleIntent(
                                ItineraryIntent.NavigateToEventOnMap(
                                    it,
                                ),
                            )
                        },
                        onAddEventClick = { screenModel.handleIntent(ItineraryIntent.CreateNewEvent) },
                        onEventClick = { eventId ->
                            mainNavigator?.push(EventDetailsScreen(tripId, eventId))
                        },
                    )
                } else {
                    ItineraryMapView(
                        events = state.events,
                        selectedEventId = state.selectedEventId,
                        onDetailsClick = { eventId ->
                            mainNavigator?.push(EventDetailsScreen(tripId, eventId))
                        },
                    ) { id ->
                        screenModel.handleIntent(ItineraryIntent.NavigateToEventOnMap(id))
                    }
                }
            }
        }
        }

        if (state.isEditorVisible) {
            EventEditorDialog(
                data = state.editorData,
                onIntent = { intent ->
                    screenModel.handleIntent(ItineraryIntent.EditorAction(intent))
                },
                participants = state.participants,
                currency = state.currency,
            )
        }
    }
}

@Composable
fun ViewModeButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) Color.White else Color.Transparent
    val shadow = if (isSelected) 1.dp else 0.dp
    val contentColor = if (isSelected) Color(0xFF0A0A0A) else Color(0xFF4A5565)

    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        colors = ButtonDefaults.buttonColors(containerColor = bgColor, contentColor = contentColor),
        shape = RoundedCornerShape(10.dp),
        elevation =
            ButtonDefaults.buttonElevation(
                defaultElevation = shadow,
                pressedElevation = shadow,
            ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Text(text, fontSize = 14.sp)
        }
    }
}

@Composable
fun DayChip(
    dayIndex: Int,
    startDate: Long,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) Color(0xFF155DFC) else Color.White
    val border = if (isSelected) Color.Transparent else Color(0xFFE5E7EB)
    val dayTextColor = if (isSelected) Color.White else Color(0xFF364153)
    val dateTextColor =
        if (isSelected) Color.White.copy(alpha = 0.8f) else Color(0xFF364153).copy(alpha = 0.8f)

    val dateMillis = startDate + (dayIndex * 24L * 60L * 60L * 1000L)
    val dateStr = dateMillis.toReadableDateRu()
    val label = if (count > 0) "$dateStr • $count" else dateStr

    Column(
        modifier =
            Modifier
                .width(96.dp)
                .background(bgColor, RoundedCornerShape(16.dp))
                .border(1.dp, border, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "День ${dayIndex + 1}",
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            color = dayTextColor,
            maxLines = 1,
        )
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = dateTextColor,
            maxLines = 1,
        )
    }
}

private val TIME_COL_WIDTH = 45.dp
private val LEFT_PADDING = 16.dp
private val DOT_SPACER = 12.dp
private val DOT_SIZE = 12.dp
private val ITEM_GAP = 24.dp
private val LINE_COLOR = Color(0xFFE5E7EB)
private val LINE_WIDTH = 2.dp

private val DOT_CENTER_X = LEFT_PADDING + TIME_COL_WIDTH + DOT_SPACER + DOT_SIZE / 2

@Composable
fun ItineraryListView(
    events: List<Event>,
    currency: String,
    onEdit: (Event) -> Unit,
    onAddEventClick: () -> Unit,
    onMapClick: (Long) -> Unit,
    onEventClick: (Long) -> Unit,
) {
    if (events.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            DSEmptyStateCard(
                title = "Нет мест в маршруте",
                description = "Добавьте первое место, чтобы начать планировать маршрут",
                buttonText = "Добавить место",
                onButtonClick = onAddEventClick,
                icon = Icons.Default.LocationOn,
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            itemsIndexed(events) { index, event ->
                EventListItem(
                    event = event,
                    currency = currency,
                    isFirst = index == 0,
                    isLast = index == events.lastIndex,
                    onEdit = onEdit,
                    onMapClick = onMapClick,
                    onEventClick = onEventClick,
                )
            }

            item {
                Spacer(Modifier.height(16.dp))
                DSButton(
                    text = "Добавить место",
                    onClick = onAddEventClick,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    isOutline = true,
                    icon = Icons.Default.Add,
                )
            }
        }
    }
}

@Composable
fun EventListItem(
    event: Event,
    currency: String,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: (Event) -> Unit,
    onMapClick: (Long) -> Unit,
    onEventClick: (Long) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize().graphicsLayer { clip = false },
        ) {
            val cx = DOT_CENTER_X.toPx()
            val dotMidY = 4.dp.toPx() + DOT_SIZE.toPx() / 2f
            val lw = LINE_WIDTH.toPx()
            val extraDown = if (!isLast) ITEM_GAP.toPx() else 0f

            if (!isFirst) {
                drawLine(LINE_COLOR, Offset(cx, 0f), Offset(cx, dotMidY), lw)
            }
            if (!isLast) {
                drawLine(LINE_COLOR, Offset(cx, dotMidY), Offset(cx, size.height + extraDown), lw)
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = LEFT_PADDING, end = 16.dp, top = 0.dp, bottom = ITEM_GAP),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(TIME_COL_WIDTH).padding(top = 2.dp),
            ) {
                Text(
                    event.time,
                    fontSize = 14.sp,
                    color = Color(0xFF4A5565),
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.width(DOT_SPACER))

            Box(
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .size(DOT_SIZE)
                        .background(
                            color =
                                if (event.status == "BOOKED") {
                                    Color(0xFF00C950)
                                } else {
                                    Color(0xFFD1D5DC)
                                },
                            shape = CircleShape,
                        ).border(2.dp, Color.White, CircleShape),
            )

            Spacer(Modifier.width(12.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
                modifier = Modifier.fillMaxWidth().clickable { onEventClick(event.id) },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            event.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0A0A0A),
                        )
                        when (event.status) {
                            "BOOKED" -> {
                                Box(
                                    Modifier
                                        .background(
                                            Color(0xFFDCFCE7),
                                            RoundedCornerShape(100.dp),
                                        ).padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        "Забронировано",
                                        fontSize = 12.sp,
                                        color = Color(0xFF008236),
                                    )
                                }
                            }

                            "PAID" -> {
                                Text(
                                    "• Оплачено",
                                    fontSize = 12.sp,
                                    color = Color(0xFF00A63E),
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            tint = Color(0xFF6A7282),
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(event.subtitle, fontSize = 14.sp, color = Color(0xFF6A7282))
                    }

                    if (!event.description.isNullOrBlank()) {
                        Text(
                            event.description,
                            fontSize = 14.sp,
                            color = Color(0xFF4A5565),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    Row(
                        Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (!event.duration.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    null,
                                    tint = Color(0xFF6A7282),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(event.duration, fontSize = 12.sp, color = Color(0xFF6A7282))
                            }
                        }
                        event.cost.takeIf { it > 0.0 }?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AttachMoney,
                                    null,
                                    tint = Color(0xFF6A7282),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "$currency ${it.toInt()}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF6A7282),
                                )
                            }
                        }
                    }

                    Row(
                        Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onEdit(event) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF3F4F6),
                                    contentColor = Color(0xFF0A0A0A),
                                ),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Изменить", fontSize = 14.sp) }

                        Button(
                            onClick = { onMapClick(event.id) },
                            modifier = Modifier.weight(1f).height(36.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEFF6FF),
                                    contentColor = Color(0xFF155DFC),
                                ),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("На карте", fontSize = 14.sp) }
                    }
                }
            }
        }
    }
}

fun getCategoryEmoji(cat: String?) =
    when (cat) {
        "FOOD" -> "🍱"
        "SIGHT" -> "⛩️"
        "SHOPPING" -> "🛍️"
        else -> "📍"
    }
