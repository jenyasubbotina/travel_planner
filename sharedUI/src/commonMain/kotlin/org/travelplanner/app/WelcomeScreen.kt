package org.travelplanner.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import kotlinx.coroutines.launch
import org.travelplanner.app.core.BaseScreenModel
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSTextInput

class WelcomeScreen : Screen {
    @Composable
    override fun Content() {
        val screenModel = getScreenModel<WelcomeScreenModel>()
        val state by screenModel.state.collectAsState()

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("👋 Привет!", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("Как вас зовут?", fontSize = 16.sp, color = Color.Gray)

            Spacer(Modifier.height(32.dp))

            DSTextInput(
                value = state.name,
                onValueChange = { screenModel.handleIntent(WelcomeIntent.NameChanged(it)) },
                placeholder = "Ваше имя",
                label = "Ваше имя",
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            DSButton(
                text = "Начать",
                onClick = { screenModel.handleIntent(WelcomeIntent.Register) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

class WelcomeScreenModel(
    private val userSession: UserSession,
) : BaseScreenModel<WelcomeState, WelcomeIntent, WelcomeEffect>(WelcomeState()) {
    override fun handleIntent(intent: WelcomeIntent) {
        when (intent) {
            is WelcomeIntent.NameChanged -> {
                updateState { copy(name = intent.name) }
            }

            is WelcomeIntent.Register -> {
                if (currentState.name.isBlank()) return
                screenModelScope.launch {
                    userSession.createNewUser(currentState.name)
                }
            }
        }
    }
}
