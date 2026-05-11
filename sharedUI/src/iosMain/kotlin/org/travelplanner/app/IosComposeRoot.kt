package org.travelplanner.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.UIViewController
import platform.UIKit.setStatusBarStyle

/**
 * Stable entry for Swift: `IosComposeRoot.shared.viewController()`.
 * Top-level `MainViewController()` from a `main.*.kt` file is not always visible to Swift.
 */
@Suppress("unused")
object IosComposeRoot {
    fun viewController(): UIViewController {
        ensureIosKoinStarted()
        return ComposeUIViewController {
            TravelPlannerRoot(onThemeChanged = { ThemeChanged(it) })
        }
    }
}

@Composable
private fun ThemeChanged(isDark: Boolean) {
    LaunchedEffect(isDark) {
        UIApplication.sharedApplication.setStatusBarStyle(
            if (isDark) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent,
        )
    }
}
