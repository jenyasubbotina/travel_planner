import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.mp.KoinPlatformTools
import org.koin.core.context.startKoin
import org.travelplanner.app.TravelPlannerRoot
import org.travelplanner.app.core.commonModule
import org.travelplanner.app.web.webModule

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    fun reportStartupError(message: String) {
        document.getElementById("composeApp")?.textContent = message
    }

    reportStartupError("Travel Planner is starting...")
    window.addEventListener("error") { event ->
        reportStartupError("Runtime error: $event")
    }
    window.addEventListener("unhandledrejection") { event ->
        reportStartupError("Unhandled promise rejection: $event")
    }

    runCatching {
        if (KoinPlatformTools.defaultContext().getOrNull() == null) {
            startKoin {
                modules(commonModule, webModule)
            }
        }
        ComposeViewport(viewportContainerId = "composeApp") {
            TravelPlannerRoot()
        }
    }.onFailure { error ->
        val message = buildString {
            appendLine("Travel Planner failed to start.")
            appendLine(error.message ?: error.toString())
        }
        reportStartupError(message)
    }
}
