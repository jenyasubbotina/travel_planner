package org.travelplanner.app.features.tripDetails.route.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.travelplanner.app.core.ForwardGeocoder
import org.travelplanner.app.core.GeocodedPlace
import org.travelplanner.app.theme.AppColors
import org.travelplanner.app.theme.DSButton

@Composable
expect fun LocationPickerMapCanvas(
    initialLatitude: Double?,
    initialLongitude: Double?,
    selectedLat: Double,
    selectedLon: Double,
    cameraTarget: Pair<Double, Double>?,
    onTap: (lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun LocationPickerMap(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit,
) {
    val startLat = initialLatitude ?: 52.52
    val startLon = initialLongitude ?: 13.40

    var selectedLat by remember { mutableStateOf(startLat) }
    var selectedLon by remember { mutableStateOf(startLon) }
    var cameraTarget by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val forwardGeocoder: ForwardGeocoder = koinInject()

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            results = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(400)
        results = forwardGeocoder.search(trimmed)
        isSearching = false
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        LocationPickerMapCanvas(
            initialLatitude = initialLatitude,
            initialLongitude = initialLongitude,
            selectedLat = selectedLat,
            selectedLon = selectedLon,
            cameraTarget = cameraTarget,
            onTap = { lat, lon ->
                selectedLat = lat
                selectedLon = lon
                query = ""
                results = emptyList()
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Найти место", color = AppColors.Gray) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = AppColors.TextSecondary)
                },
                trailingIcon = {
                    when {
                        isSearching -> {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 4.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        query.isNotEmpty() -> {
                            IconButton(onClick = {
                                query = ""
                                results = emptyList()
                            }) {
                                Icon(Icons.Default.Close, "Очистить", tint = AppColors.TextSecondary)
                            }
                        }
                        else -> Unit
                    }
                },
                singleLine = true,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = AppColors.Primary,
                        unfocusedBorderColor = AppColors.Border,
                    ),
            )

            if (results.isNotEmpty()) {
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(max = 320.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(6.dp),
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        results.forEachIndexed { index, place ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedLat = place.latitude
                                            selectedLon = place.longitude
                                            cameraTarget = place.latitude to place.longitude
                                            query = ""
                                            results = emptyList()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    null,
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    place.displayName,
                                    fontSize = 14.sp,
                                    color = AppColors.TextPrimary,
                                    maxLines = 2,
                                )
                            }
                            if (index < results.lastIndex) {
                                HorizontalDivider(color = AppColors.Border)
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Выбрано: ${selectedLat.toString().take(7)}, ${selectedLon.toString().take(7)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DSButton(
                    text = "Отмена",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    backgroundColor = AppColors.ChipBgInactive,
                    contentColor = Color.Black,
                )

                DSButton(
                    text = "Подтвердить",
                    onClick = { onConfirm(selectedLat, selectedLon) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
