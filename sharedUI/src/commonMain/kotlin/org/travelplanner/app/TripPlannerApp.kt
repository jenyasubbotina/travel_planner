package org.travelplanner.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import org.koin.compose.koinInject
import org.travelplanner.app.core.GlobalNotifier
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.features.tripList.TripListScreen
import org.travelplanner.app.theme.AppTheme

@Composable
fun TravelPlannerRoot(
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {},
) = AppTheme(onThemeChanged) {
    TripPlannerApp()
}

@Composable
fun TripPlannerApp() {
    val userSession: UserSession = koinInject()
    val currentUser by userSession.currentUser.collectAsState()
    val isLoaded by userSession.isLoaded.collectAsState()

    val globalNotifier = koinInject<GlobalNotifier>()
    val snackbarHostState = remember { SnackbarHostState() }

    var errorDialogMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        globalNotifier.successes.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        globalNotifier.errors.collect { msg ->
            errorDialogMessage = msg
        }
    }

    if (errorDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { errorDialogMessage = null },
            title = { Text("Внимание") },
            text = { Text(errorDialogMessage!!) },
            confirmButton = {
                TextButton(
                    onClick = { errorDialogMessage = null },
                ) {
                    Text("Понятно")
                }
            },
        )
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .background(MaterialTheme.colorScheme.background),
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) {
            Crossfade(
                targetState =
                    when {
                        !isLoaded -> "splash"
                        currentUser == null -> "onboarding"
                        else -> "main"
                    },
                animationSpec = tween(400),
            ) { state ->
                when (state) {
                    "splash" -> {
                        SplashScreen()
                    }

                    "onboarding" -> {
                        OnboardingFlow(userSession)
                    }

                    "main" -> {
                        Navigator(TripListScreen()) { navigator ->
                            navigator.lastItem.Content()
                        }
                    }
                }
            }
        }
    }
}
