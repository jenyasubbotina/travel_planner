package org.travelplanner.app.features.tripDetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.internal.BackHandler
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.core.GatewayConfigManager
import org.travelplanner.app.core.auth.AuthTokenManager
import org.travelplanner.app.core.TripUtils
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.data.TripDetailsEffect
import org.travelplanner.app.data.TripDetailsIntent
import org.travelplanner.app.data.TripDetailsScreenModel
import org.travelplanner.app.features.profile.ui.ProfileAvatar
import org.travelplanner.app.features.tripDetails.balance.BalanceTab
import org.travelplanner.app.features.tripDetails.expenses.ExpensesTab
import org.travelplanner.app.features.tripDetails.more.MoreTab
import org.travelplanner.app.features.tripDetails.route.ui.ItineraryTab
import org.travelplanner.app.features.tripDetails.summary.TripSummaryTab
import org.travelplanner.app.features.tripList.TripListScreen

data class TripDetailsScreen(
    val tripId: String,
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class, InternalVoyagerApi::class)
    @Composable
    override fun Content() {
        val screenModel = getScreenModel<TripDetailsScreenModel> { parametersOf(tripId) }
        val networkState by screenModel.networkState.collectAsState()
        val retryCountdown by screenModel.retryCountdown.collectAsState()
        val tripSyncState by screenModel.state.collectAsState()
        val userSession = koinInject<UserSession>()
        val gatewayManager = koinInject<GatewayConfigManager>()
        val authTokenManager = koinInject<AuthTokenManager>()
        val gatewayConfig by gatewayManager.config.collectAsState()
        val scope = rememberCoroutineScope()

        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            screenModel.effect.collect { effect ->
                when (effect) {
                    is TripDetailsEffect.KickUser -> navigator.popUntil { it is TripListScreen }
                }
            }
        }

        val summaryTab = remember(tripId) { TripSummaryTab(tripId) }
        val itineraryTab = remember(tripId) { ItineraryTab(tripId) }
        val expensesTab = remember(tripId) { ExpensesTab(tripId) }
        val balanceTab = remember(tripId) { BalanceTab(tripId) }
        val moreTab = remember(tripId) { MoreTab(tripId) }

        val initialTab =
            remember {
                screenModel.tabHistory.lastOrNull() ?: summaryTab
            }

        TabNavigator(initialTab) { tabNavigator ->

            val currentTab = tabNavigator.current
            LaunchedEffect(currentTab) {
                if (screenModel.tabHistory.lastOrNull() != currentTab) {
                    screenModel.tabHistory.add(currentTab)
                }
            }

            BackHandler(enabled = screenModel.tabHistory.size > 1) {
                screenModel.tabHistory.removeLastOrNull()
                screenModel.tabHistory.lastOrNull()?.let { tabNavigator.current = it }
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            val trip = tripSyncState.trip
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = trip?.title?.takeIf { it.isNotBlank() } ?: "Поездка",
                                    fontSize = 18.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0A0A0A),
                                )
                                val dateRange = TripUtils.formatDateRangeRu(trip?.startDate, trip?.endDate)
                                if (dateRange.isNotBlank()) {
                                    Text(
                                        text = dateRange,
                                        fontSize = 12.sp,
                                        lineHeight = 14.sp,
                                        color = Color(0xFF6A7282),
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { navigator.pop() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "К поездкам",
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                            ),
                        actions = {
                            SyncIndicator(
                                networkState = networkState,
                                syncState = tripSyncState.syncState,
                                pendingCount = tripSyncState.pendingCount,
                                conflicts = tripSyncState.conflicts,
                                deadEntries = tripSyncState.deadEntries,
                                depthAlert = tripSyncState.depthAlert,
                                retrySeconds = retryCountdown,
                                pendingApprovalsCount = tripSyncState.pendingApprovalsCount,
                                pendingProposalsCount = tripSyncState.pendingProposalsCount,
                                currentConfig = gatewayConfig,
                                onConfigSave = { newConfig ->
                                    scope.launch {
                                        if (newConfig.address != gatewayConfig.address) {
                                            authTokenManager.logout()
                                        }
                                        gatewayManager.updateConfig(newConfig)
                                    }
                                },
                                onRetrySync = { screenModel.handleIntent(TripDetailsIntent.PerformSync) },
                                onDiscardDeadEntry = { entryId ->
                                    screenModel.handleIntent(TripDetailsIntent.DiscardDeadEntry(entryId))
                                },
                                onRetryDeadEntry = { entryId ->
                                    screenModel.handleIntent(TripDetailsIntent.RetryEntry(entryId))
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            ProfileAvatar(userSession = userSession, navigator = navigator)
                            Spacer(Modifier.width(8.dp))
                        },
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.White,
                        contentColor = Color(0xFF6A7282),
                        tonalElevation = 8.dp,
                    ) {
                        TabNavigationItem(summaryTab, tabNavigator, screenModel.tabHistory)
                        TabNavigationItem(itineraryTab, tabNavigator, screenModel.tabHistory)
                        TabNavigationItem(expensesTab, tabNavigator, screenModel.tabHistory)
                        TabNavigationItem(balanceTab, tabNavigator, screenModel.tabHistory)
                        TabNavigationItem(moreTab, tabNavigator, screenModel.tabHistory)
                    }
                },
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    CurrentTab()
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(
    tab: Tab,
    tabNavigator: TabNavigator,
    tabHistory: SnapshotStateList<Tab>,
) {
    val selected = tabNavigator.current == tab

    NavigationBarItem(
        selected = selected,
        onClick = {
            if (tabNavigator.current != tab) {
                tabHistory.removeAll { it == tab }
                tabHistory.add(tab)
                tabNavigator.current = tab
            }
        },
        icon = {
            Icon(painter = tab.options.icon!!, contentDescription = tab.options.title)
        },
        label = { Text(tab.options.title) },
        colors =
            NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF155DFC),
                selectedTextColor = Color(0xFF155DFC),
                unselectedIconColor = Color(0xFF6A7282),
                unselectedTextColor = Color(0xFF6A7282),
                indicatorColor = Color.Transparent,
            ),
    )
}
