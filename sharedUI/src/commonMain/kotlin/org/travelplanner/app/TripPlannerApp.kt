package org.travelplanner.app

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.Navigator
import org.koin.compose.koinInject
import org.travelplanner.app.core.GlobalNotifier
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.features.tripList.TripListScreen

@Composable
fun TripPlannerApp() {
    val userSession: UserSession = koinInject()
    val currentUser by userSession.currentUser.collectAsState()
    val availableUsers by userSession.availableUsers.collectAsState()
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
                        if (availableUsers.isNotEmpty()) {
                            DebugUserSwitcher(userSession, isLoginScreen = true)
                        }
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

@Composable
fun DebugHeader(
    navigator: Navigator,
    userSession: UserSession,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigator.canPop) {
            Button(onClick = { navigator.pop() }) { Text("Back") }
        }
        Spacer(Modifier.weight(1f))
        DebugUserSwitcher(userSession, isLoginScreen = false)
    }
}

@Composable
fun DebugUserSwitcher(
    userSession: UserSession,
    isLoginScreen: Boolean,
) {
    val currentUser by userSession.currentUser.collectAsState()
    val availableUsers by userSession.availableUsers.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    val label = currentUser?.name?.take(3)?.plus("..") ?: "Login"

    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .background(Color(0xFF1F2937).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                Icons.Default.Person,
                null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF1F2937),
            )
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, color = Color(0xFF1F2937), fontWeight = FontWeight.Medium)
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("+ Add New User", color = Color.Blue) },
                onClick = {
                    userSession.logout()
                    expanded = false
                },
            )
            Divider()

            availableUsers.forEach { user ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(user.name, fontWeight = FontWeight.Bold)
                            Text("ID: ${user.id}", fontSize = 10.sp)
                        }
                    },
                    onClick = {
                        userSession.switchUser(user.id)
                        expanded = false
                    },
                    leadingIcon = {
                        if (user.id == currentUser?.id) {
                            Icon(Icons.Default.Check, null)
                        }
                    },
                )
            }
        }
    }
}
