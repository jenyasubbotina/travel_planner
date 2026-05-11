package org.travelplanner.app.features.tripDetails.route.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
actual fun LocationPickerMapCanvas(
    initialLatitude: Double?,
    initialLongitude: Double?,
    selectedLat: Double,
    selectedLon: Double,
    cameraTarget: Pair<Double, Double>?,
    onTap: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFFE5E7EB)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Карта недоступна на этой платформе",
            color = Color(0xFF6A7282),
            modifier = Modifier.padding(24.dp),
        )
    }
}
