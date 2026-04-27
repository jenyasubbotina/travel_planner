package org.travelplanner.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.screen.Screen
import org.koin.compose.koinInject
import org.travelplanner.app.auth.AuthForm
import org.travelplanner.app.core.UserSession

class WelcomeScreen : Screen {
    @Composable
    override fun Content() {
        val userSession: UserSession = koinInject()

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.White),
        ) {
            AuthForm(userSession = userSession)
        }
    }
}
