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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MailOutline
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
import org.travelplanner.app.core.GatewayConfigManager
import org.travelplanner.app.core.TripUtils.formatDateRangeRuAbbr
import org.travelplanner.app.core.TripUtils.formatNumber
import org.travelplanner.app.core.TripUtils.isoToEpochMillis
import org.travelplanner.app.core.TripUtils.pluralizeDays
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.auth.AuthTokenManager
import org.travelplanner.app.core.rememberResolvedImageUrl
import org.travelplanner.app.data.GlobalSyncManager
import org.travelplanner.app.data.NetworkState
import org.travelplanner.app.data.SyncState
import org.travelplanner.app.domain.Trip
import org.travelplanner.app.features.profile.ui.ProfileAvatar
import org.travelplanner.app.features.tripDetails.SyncIndicator
import org.travelplanner.app.features.tripDetails.TripDetailsScreen
import org.travelplanner.app.theme.DSButton
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
        val authTokenManager = koinInject<AuthTokenManager>()
        val networkState by globalSyncManager.networkState.collectAsState()
        val retryCountdown by globalSyncManager.retryCountdown.collectAsState()
        val gatewayConfig by gatewayManager.config.collectAsState()

        val scope = rememberCoroutineScope()
        var isFabExpanded by remember { mutableStateOf(false) }
        var showJoinDialog by remember { mutableStateOf(false) }

        var showAcceptInvitationDialog by remember { mutableStateOf(false) }

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
                        SyncIndicator(
                            networkState = networkState,
                            syncState = SyncState.UP_TO_DATE,
                            pendingCount = state.pendingCount,
                            retrySeconds = retryCountdown,
                            currentConfig = gatewayConfig,
                            onConfigSave = { newConfig ->
                                scope.launch {
                                    if (newConfig.address != gatewayConfig.address) {
                                        authTokenManager.logout()
                                    }
                                    gatewayManager.updateConfig(newConfig)
                                }
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        ProfileAvatar(userSession = userSession, navigator = navigator)
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
                                "По приглашению",
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
                                    showAcceptInvitationDialog = true
                                },
                                containerColor = Color.White,
                                contentColor = Color(0xFF155DFC),
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.MailOutline,
                                    contentDescription = "Accept invitation",
                                )
                            }
                        }

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
                    verticalAlignment = Alignment.CenterVertically,
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
                        leadingIcon = Icons.Default.CalendarToday,
                    )
                    DSTextChip(
                        text = "Архив",
                        isActive = state.activeFilter == TripFilter.ARCHIVED,
                        onClick = { screenModel.handleIntent(TripListIntent.FilterChange(TripFilter.ARCHIVED)) },
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { screenModel.handleIntent(TripListIntent.ToggleSortOrder) }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Сортировка")
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
                                val invitationId = state.pendingInvitationByTripId[trip.id]
                                PendingTripCard(
                                    trip = trip,
                                    invitationId = invitationId,
                                    onRefresh = { screenModel.handleIntent(TripListIntent.Refresh) },
                                    onAccept = {
                                        invitationId?.let {
                                            screenModel.handleIntent(
                                                TripListIntent.AcceptPendingInvitation(
                                                    it,
                                                ),
                                            )
                                        }
                                    },
                                    onDecline = {
                                        invitationId?.let {
                                            screenModel.handleIntent(
                                                TripListIntent.DeclinePendingInvitation(
                                                    it,
                                                ),
                                            )
                                        }
                                    },
                                )
                            } else {
                                TripCard(trip) {
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
                isOffline = networkState != NetworkState.ONLINE,
            )
        }

        if (showAcceptInvitationDialog) {
            AcceptInvitationDialog(
                onDismiss = { showAcceptInvitationDialog = false },
                onSubmit = { id ->
                    screenModel.handleIntent(TripListIntent.AcceptInvitation(id))
                    showAcceptInvitationDialog = false
                },
                isOffline = networkState != NetworkState.ONLINE,
            )
        }
    }
}

@Composable
fun TripCard(
    trip: Trip,
    onClick: () -> Unit,
) {
    val currentMillis = Clock.System.now().toEpochMilliseconds()
    val startMillis = isoToEpochMillis(trip.startDate)
    val endMillis = isoToEpochMillis(trip.endDate)
    val daysToStart = ceil((startMillis - currentMillis) / (1000.0 * 60 * 60 * 24)).toInt()

    val totalBudget = trip.totalBudget.toDoubleOrNull() ?: 0.0
    val spentAmount = trip.spentAmount.toDoubleOrNull() ?: 0.0

    val statusText =
        when {
            currentMillis < startMillis -> {
                if (daysToStart == 1) "Завтра" else "Через $daysToStart ${pluralizeDays(daysToStart)}"
            }

            currentMillis > endMillis -> {
                "Завершена"
            }

            else -> {
                "В поездке"
            }
        }

    val statusBgColor =
        when {
            currentMillis < startMillis -> Color(0xFF00C950)
            currentMillis > endMillis -> Color(0xFF4A5565)
            else -> Color(0xFF155DFC)
        }

    val progress =
        if (totalBudget > 0) {
            (spentAmount / totalBudget).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

    val remainingBudget = totalBudget - spentAmount

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier =
            Modifier
                .fillMaxWidth()
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
                    val resolved = rememberResolvedImageUrl(trip.imageUrl)
                    AsyncImage(
                        model = resolved,
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
                            .background(statusBgColor, RoundedCornerShape(16.dp))
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = trip.destination,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                        )
                    }
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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = Color(0xFF6A7282),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = formatDateRangeRuAbbr(trip.startDate, trip.endDate),
                            color = Color(0xFF4A5565),
                            fontSize = 14.sp,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = Color(0xFF6A7282),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "${trip.participantCount}",
                            color = Color(0xFF4A5565),
                            fontSize = 14.sp,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Бюджет", color = Color(0xFF4A5565), fontSize = 14.sp)
                    Text(
                        text = "${formatNumber(spentAmount)} / ${formatNumber(totalBudget)} ${trip.currency}",
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = if (remainingBudget >= 0) Color(0xFF6A7282) else Color(0xFFDC2626),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text =
                            if (remainingBudget >= 0) {
                                "Осталось ${formatNumber(remainingBudget)} ${trip.currency}"
                            } else {
                                "Превышен на ${formatNumber(-remainingBudget)} ${trip.currency}"
                            },
                        fontSize = 12.sp,
                        color = if (remainingBudget >= 0) Color(0xFF6A7282) else Color(0xFFDC2626),
                    )
                }
            }
        }
    }
}

@Composable
fun PendingTripCard(
    trip: Trip,
    invitationId: String? = null,
    onRefresh: () -> Unit = {},
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
) {
    val isInvitation = invitationId != null
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isInvitation) "Вас пригласили в поездку" else "Ожидание подтверждения",
                        fontSize = 12.sp,
                        color = Color(0xFFD97706),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = trip.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    if (trip.destination.isNotBlank()) {
                        Text(text = trip.destination, fontSize = 14.sp, color = Color.Gray)
                    }
                }

                if (!isInvitation) {
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

            if (isInvitation) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DSButton(
                        text = "Принять",
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                    )
                    DSButton(
                        text = "Отклонить",
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                        isOutline = true,
                    )
                }
            }
        }
    }
}
