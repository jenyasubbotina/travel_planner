package org.travelplanner.app.features.tripList

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.travelplanner.app.DSEmptyStateCard
import org.travelplanner.app.DebugUserSwitcher
import org.travelplanner.app.core.GatewayConfigManager
import org.travelplanner.app.core.TripUtils.toReadableDate
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.data.GlobalSyncManager
import org.travelplanner.app.data.SyncState
import org.travelplanner.app.domain.Trip
import org.travelplanner.app.features.tripDetails.SyncIndicator
import org.travelplanner.app.features.tripDetails.TripDetailsScreen
import org.travelplanner.app.theme.DSTextChip
import org.travelplanner.app.theme.DSTextInput
import kotlin.math.ceil
import kotlin.time.Clock

class TripListScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<TripListScreenModel>()
        val state by screenModel.state.collectAsState()

        val userSession = koinInject<UserSession>()
        val globalSyncManager = koinInject<GlobalSyncManager>()
        val gatewayManager = koinInject<GatewayConfigManager>()
        val networkState by globalSyncManager.networkState.collectAsState()
        val retryCountdown by globalSyncManager.retryCountdown.collectAsState()
        val gatewayConfig by gatewayManager.config.collectAsState()

        val scope = rememberCoroutineScope()
        var isFabExpanded by remember { mutableStateOf(false) }
        var showJoinDialog by remember { mutableStateOf(false) }

        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            screenModel.effect.collect { effect ->
                when (effect) {
                    is TripListEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Поездки") },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                    actions = {
                        DebugUserSwitcher(userSession, isLoginScreen = false)
                        Spacer(Modifier.width(8.dp))
                        SyncIndicator(
                            networkState = networkState,
                            syncState = SyncState.UP_TO_DATE,
                            retrySeconds = retryCountdown,
                            currentConfig = gatewayConfig,
                            onConfigSave = { scope.launch { gatewayManager.updateConfig(it) } },
                        ) { }
                        Spacer(Modifier.width(8.dp))
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (isFabExpanded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp),
                        ) {
                            Text(
                                "По коду",
                                modifier =
                                    Modifier
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    showJoinDialog = true
                                },
                                containerColor = Color.White,
                                contentColor = Color(0xFF155DFC),
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Default.VpnKey, contentDescription = "Join")
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp),
                        ) {
                            Text(
                                "Создать",
                                modifier =
                                    Modifier
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 14.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = {
                                    isFabExpanded = false
                                    navigator.push(CreateTripScreen())
                                },
                                containerColor = Color.White,
                                contentColor = Color(0xFF155DFC),
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Default.FlightTakeoff, contentDescription = "Create")
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = { isFabExpanded = !isFabExpanded },
                        containerColor = Color(0xFF155DFC),
                        contentColor = Color.White,
                    ) {
                        Icon(
                            if (isFabExpanded) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = "Menu",
                        )
                    }
                }
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF9FAFB))
                        .padding(paddingValues)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DSTextInput(
                    value = state.searchQuery,
                    onValueChange = { screenModel.handleIntent(TripListIntent.Search(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Поиск поездок...",
                    icon = Icons.Default.Search,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DSTextChip(
                        text = "Все",
                        isActive = state.activeFilter == TripFilter.ALL,
                        onClick = { screenModel.handleIntent(TripListIntent.FilterChange(TripFilter.ALL)) },
                    )
                    DSTextChip(
                        text = "Предстоящие",
                        isActive = state.activeFilter == TripFilter.UPCOMING,
                        onClick = { screenModel.handleIntent(TripListIntent.FilterChange(TripFilter.UPCOMING)) },
                    )
                    DSTextChip(
                        text = "Архив",
                        isActive = state.activeFilter == TripFilter.ARCHIVED,
                        onClick = { screenModel.handleIntent(TripListIntent.FilterChange(TripFilter.ARCHIVED)) },
                    )
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }

                if (state.trips.isEmpty()) {
                    DSEmptyStateCard(
                        title = "Нет поездок",
                        description = "Создайте первую поездку и начните планировать незабываемое путешествие",
                        buttonText = "Создать поездку",
                        onButtonClick = { navigator.push(CreateTripScreen()) },
                        icon = Icons.Default.FlightTakeoff,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.trips) { trip ->
                            if (trip.status == "PENDING_JOIN") {
                                PendingTripCard(
                                    trip = trip,
                                    onRefresh = { screenModel.handleIntent(TripListIntent.Refresh) },
                                )
                            } else {
                                TripCard(trip, screenModel::resolveUrl) {
                                    navigator.push(
                                        TripDetailsScreen(trip.id),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showJoinDialog) {
            JoinTripDialog(
                onDismiss = { showJoinDialog = false },
                onSubmit = { code ->
                    screenModel.handleIntent(TripListIntent.RequestJoin(code))
                    showJoinDialog = false
                },
            )
        }
    }
}

@Composable
fun TripCard(
    trip: Trip,
    resolveUrl: (String?) -> String?,
    onClick: () -> Unit,
) {
    val currentMillis = Clock.System.now().toEpochMilliseconds()
    val daysToStart = ceil((trip.startDate - currentMillis) / (1000.0 * 60 * 60 * 24)).toInt()

    val statusText =
        when {
            currentMillis < trip.startDate -> if (daysToStart == 1) "Завтра" else "Через $daysToStart дн."
            currentMillis > trip.endDate -> "Завершена"
            else -> "В поездке"
        }

    val progress =
        if (trip.totalBudget > 0) {
            (trip.spentAmount / trip.totalBudget).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val remainingBudget = trip.totalBudget - trip.spentAmount

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(bottom = 8.dp)
                .clickable(onClick = onClick),
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp),
            ) {
                if (trip.imageUrl != null) {
                    AsyncImage(
                        model = resolveUrl(trip.imageUrl),
                        contentDescription = "Trip Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Gray))
                }

                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))

                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = statusText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                ) {
                    Text(
                        text = trip.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = trip.destination,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                    )
                }
            }

            Column(
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${trip.startDate.toReadableDate()} — ${trip.endDate.toReadableDate()}",
                        color = Color(0xFF4A5565),
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "${trip.participantCount} \uD83D\uDC64",
                        color = Color(0xFF4A5565),
                        fontSize = 14.sp,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Бюджет", color = Color(0xFF4A5565), fontSize = 14.sp)
                    Text(
                        text = "${trip.spentAmount.toInt()} / ${trip.totalBudget.toInt()} ${trip.currency}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = if (remainingBudget < 0) Color(0xFFDC2626) else Color(0xFF0A0A0A),
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    color = if (remainingBudget >= 0) Color(0xFF00C950) else Color(0xFFDC2626),
                    trackColor = Color(0xFFF3F4F6),
                )

                Text(
                    text =
                        if (remainingBudget >= 0) {
                            "Осталось ${remainingBudget.toInt()} ${trip.currency}"
                        } else {
                            "Превышен на ${(-remainingBudget).toInt()} ${trip.currency}"
                        },
                    fontSize = 12.sp,
                    color = if (remainingBudget >= 0) Color(0xFF6A7282) else Color(0xFFDC2626),
                )
            }
        }
    }
}

@Composable
fun PendingTripCard(
    trip: Trip,
    onRefresh: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ожидание подтверждения",
                    fontSize = 12.sp,
                    color = Color(0xFFD97706),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = trip.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(text = trip.destination, fontSize = 14.sp, color = Color.Gray)
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.background(Color.White, CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Обновить статус",
                    tint = Color(0xFFD97706),
                )
            }
        }
    }
}
