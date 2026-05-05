package org.travelplanner.app.features.tripDetails.route.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.travelplanner.app.domain.Event

@Composable
expect fun ItineraryMap(
    events: List<Event>,
    selectedEventId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun ItineraryMapView(
    events: List<Event>,
    selectedEventId: String?,
    onDetailsClick: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE5E7EB))) {
        ItineraryMap(
            events = events,
            selectedEventId = selectedEventId,
            onSelect = onSelect,
            modifier = Modifier.fillMaxSize(),
        )

        if (selectedEventId != null) {
            val event = events.find { it.id == selectedEventId }
            if (event != null) {
                SelectedEventCard(
                    event = event,
                    onDetailsClick = onDetailsClick,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun SelectedEventCard(
    event: Event,
    onDetailsClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp)
                .clickable(enabled = false) {},
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(10.dp),
    ) {
        Column(Modifier.padding(24.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(48.dp)
                    .height(4.dp)
                    .background(Color(0xFFD1D5DC), CircleShape),
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.Top) {
                Text(getCategoryEmoji(event.category), fontSize = 30.sp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(event.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            tint = Color(0xFF6A7282),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            event.subtitle,
                            fontSize = 14.sp,
                            color = Color(0xFF6A7282),
                        )
                    }
                    if (!event.description.isNullOrBlank()) {
                        Text(
                            event.description ?: "",
                            fontSize = 14.sp,
                            color = Color(0xFF4A5565),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    Row(
                        Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {},
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF155DFC),
                                ),
                        ) {
                            Text("Маршрут")
                        }
                        Button(
                            onClick = { onDetailsClick(event.id) },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF3F4F6),
                                    contentColor = Color.Black,
                                ),
                        ) {
                            Text("Подробнее")
                        }
                    }
                }
            }
        }
    }
}
