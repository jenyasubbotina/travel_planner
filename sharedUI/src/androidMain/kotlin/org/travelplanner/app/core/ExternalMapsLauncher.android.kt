package org.travelplanner.app.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberExternalMapsLauncher(): ExternalMapsLauncher {
    val context = LocalContext.current
    return ExternalMapsLauncher(context)
}

actual class ExternalMapsLauncher(
    private val context: Context,
) {
    actual fun launch(
        latitude: Double,
        longitude: Double,
        label: String?,
        mode: MapsLaunchMode,
    ) {
        val encodedLabel =
            label
                ?.replace("(", " ")
                ?.replace(")", " ")
                ?.trim()
                .orEmpty()

        when (mode) {
            MapsLaunchMode.DIRECTIONS -> {
                val navIntent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("google.navigation:q=$latitude,$longitude"),
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                try {
                    context.startActivity(navIntent)
                    return
                } catch (_: ActivityNotFoundException) {
                }
                val geoUri =
                    if (encodedLabel.isNotEmpty()) {
                        Uri.parse("geo:0,0?q=$latitude,$longitude($encodedLabel)")
                    } else {
                        Uri.parse("geo:0,0?q=$latitude,$longitude")
                    }
                startWithChooser(Intent(Intent.ACTION_VIEW, geoUri), "Маршрут")
            }

            MapsLaunchMode.VIEW -> {
                val geoUri =
                    if (encodedLabel.isNotEmpty()) {
                        Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($encodedLabel)")
                    } else {
                        Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
                    }
                startWithChooser(Intent(Intent.ACTION_VIEW, geoUri), "Открыть на карте")
            }
        }
    }

    private fun startWithChooser(
        intent: Intent,
        title: String,
    ) {
        try {
            val chooser =
                Intent
                    .createChooser(intent, title)
                    .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
        }
    }
}
