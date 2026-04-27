import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cafe.adriel.voyager.navigator.Navigator
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.io.files.Path
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.travelplanner.app.WelcomeScreen
import org.travelplanner.app.core.DriverFactory
import org.travelplanner.app.core.GatewayConfig
import org.travelplanner.app.core.GatewayConfigManager
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.auth.AuthSession
import org.travelplanner.app.core.commonModule
import org.travelplanner.app.features.tripList.TripListScreen
import java.io.File

val desktopModule =
    module {
        single { DriverFactory().createDriver() }

        single<KStore<AuthSession>> {
            val home = System.getProperty("user.home")
            val dir = File(home, ".travel-planner")
            dir.mkdirs()

            val path = File(dir, "auth_session.json").absolutePath
            storeOf(
                file = Path(path),
                default =
                    AuthSession(
                        accessToken = "",
                        refreshToken = "",
                        userId = "",
                        email = "",
                        displayName = "",
                    ),
            )
        }

        single<KStore<List<AuthSession>>>(named("accounts")) {
            val home = System.getProperty("user.home")
            val dir = File(home, ".travel-planner")
            dir.mkdirs()

            val path = File(dir, "saved_accounts.json").absolutePath
            storeOf(file = Path(path), default = emptyList())
        }

        single {
            val home = System.getProperty("user.home")
            val dir = File(home, ".travel-planner")
            dir.mkdirs()

            val path = File(dir, "gateway.json").absolutePath
            GatewayConfigManager(storeOf<GatewayConfig>(Path(path)))
        }
    }

fun main() =
    application {
        val koinApp =
            remember {
                startKoin {
                    modules(commonModule, desktopModule)
                }
            }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Travel Planner",
            state = rememberWindowState(size = DpSize(450.dp, 800.dp)),
        ) {
            val userSession: UserSession = koinInject()
            val currentUser by userSession.currentUser.collectAsState()

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .background(MaterialTheme.colorScheme.background),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBars)
                            .background(MaterialTheme.colorScheme.background),
                ) {
                    if (currentUser == null) {
                        Navigator(WelcomeScreen())
                    } else {
                        Navigator(TripListScreen()) { navigator ->
                            navigator.lastItem.Content()
                        }
                    }
                }
            }
        }
    }
