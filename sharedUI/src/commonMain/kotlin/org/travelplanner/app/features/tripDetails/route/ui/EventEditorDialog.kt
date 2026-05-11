package org.travelplanner.app.features.tripDetails.route.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.travelplanner.app.core.Validation
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.features.tripDetails.route.detailed.ui.EventIntent
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSTextInput

data class EventEditData(
    val title: String = "",
    val subtitle: String = "",
    val address: String = "",
    val description: String = "",
    val time: String = "",
    val cost: String = "",
    val duration: String = "",
    val status: String = "NONE",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isMapPickerOpen: Boolean = false,
    val dayIndex: Int = 0,
    val eventId: String? = null,
    val participantIds: List<String> = emptyList(),
    val showErrors: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditorDialog(
    data: EventEditData,
    onIntent: (EventIntent) -> Unit,
    participants: List<Participant> = emptyList(),
    currency: String = "¥",
) {
    Dialog(
        onDismissRequest = { onIntent(EventIntent.CloseEditor) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.92f)
                        .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(8.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Редактировать",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827),
                        )
                        IconButton(onClick = { onIntent(EventIntent.CloseEditor) }) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF9CA3AF))
                        }
                    }

                    val timeError =
                        if (data.showErrors && !Validation.isValidTimeHhMm(data.time)) {
                            "Время в формате ЧЧ:ММ"
                        } else {
                            null
                        }
                    val costError =
                        if (data.showErrors && data.cost.isNotBlank() &&
                            !Validation.isNonNegativeAmount(data.cost)
                        ) {
                            "Должно быть числом ≥ 0"
                        } else {
                            null
                        }
                    val titleError =
                        if (data.showErrors && !Validation.isValidTitle(data.title)) {
                            if (data.title.isBlank()) {
                                "Введите название"
                            } else {
                                "Слишком длинное (макс. ${Validation.TITLE_MAX})"
                            }
                        } else {
                            null
                        }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DSTextInput(
                            value = data.time,
                            onValueChange = { v ->
                                onIntent(
                                    EventIntent.UpdateEditorField {
                                        copy(
                                            time = v,
                                        )
                                    },
                                )
                            },
                            placeholder = "10:00",
                            label = "Время",
                            isError = timeError != null,
                            errorMessage = timeError,
                            modifier = Modifier.weight(1f),
                        )
                        DSTextInput(
                            value = data.cost,
                            onValueChange = { v ->
                                onIntent(
                                    EventIntent.UpdateEditorField {
                                        copy(
                                            cost = v,
                                        )
                                    },
                                )
                            },
                            placeholder = "0",
                            label = "Бюджет ($currency)",
                            isError = costError != null,
                            errorMessage = costError,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    DSTextInput(
                        value = data.title,
                        onValueChange = { v -> onIntent(EventIntent.UpdateEditorField { copy(title = v) }) },
                        placeholder = "Название",
                        label = "Название",
                        isError = titleError != null,
                        errorMessage = titleError,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    DSTextInput(
                        value = data.subtitle,
                        onValueChange = { v ->
                            onIntent(
                                EventIntent.UpdateEditorField {
                                    copy(
                                        subtitle = v,
                                    )
                                },
                            )
                        },
                        placeholder = "Район / Ориентир",
                        label = "Место",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    DSTextInput(
                        value = data.description,
                        onValueChange = { v ->
                            onIntent(
                                EventIntent.UpdateEditorField {
                                    copy(
                                        description = v,
                                    )
                                },
                            )
                        },
                        placeholder = "Описание",
                        label = "Описание",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 3,
                    )

                    DSTextInput(
                        value = data.duration,
                        onValueChange = { v ->
                            val cleaned =
                                v
                                    .filter { it.isDigit() || it == '.' || it == ',' }
                                    .replace(',', '.')
                                    .let { s ->
                                        val firstDot = s.indexOf('.')
                                        if (firstDot < 0) {
                                            s
                                        } else {
                                            s.substring(0, firstDot + 1) +
                                                s.substring(firstDot + 1).replace(".", "")
                                        }
                                    }
                            onIntent(EventIntent.UpdateEditorField { copy(duration = cleaned) })
                        },
                        placeholder = "2",
                        label = "Длительность (ч)",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Статус",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF374151),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatusSegment(
                                label = "Не указан",
                                isSelected = data.status == "NONE",
                                onClick = {
                                    onIntent(EventIntent.UpdateEditorField { copy(status = "NONE") })
                                },
                            )
                            StatusSegment(
                                label = "Забронировано",
                                isSelected = data.status == "BOOKED",
                                onClick = {
                                    onIntent(EventIntent.UpdateEditorField { copy(status = "BOOKED") })
                                },
                            )
                            StatusSegment(
                                label = "Оплачено",
                                isSelected = data.status == "PAID",
                                onClick = {
                                    onIntent(EventIntent.UpdateEditorField { copy(status = "PAID") })
                                },
                            )
                        }
                    }

                    if (participants.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.People,
                                    null,
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Участники",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF374151),
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                participants.filter { it.role != "LEFT" }.forEach { participant ->
                                    val isSelected = participant.userId in data.participantIds
                                    Box(
                                        modifier =
                                            Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(
                                                    if (isSelected) {
                                                        Color(0xFFEFF6FF)
                                                    } else {
                                                        Color(
                                                            0xFFF3F4F6,
                                                        )
                                                    },
                                                ).then(
                                                    if (isSelected) {
                                                        Modifier.border(
                                                            1.dp,
                                                            Color(0xFF155DFC),
                                                            RoundedCornerShape(20.dp),
                                                        )
                                                    } else {
                                                        Modifier
                                                    },
                                                ).clickable {
                                                    val newIds =
                                                        if (isSelected) {
                                                            data.participantIds - participant.userId!!
                                                        } else {
                                                            data.participantIds + participant.userId!!
                                                        }
                                                    onIntent(
                                                        EventIntent.UpdateEditorField {
                                                            copy(participantIds = newIds)
                                                        },
                                                    )
                                                }.padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    null,
                                                    tint = Color(0xFF155DFC),
                                                    modifier = Modifier.size(14.dp),
                                                )
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(
                                                participant.name,
                                                fontSize = 13.sp,
                                                color =
                                                    if (isSelected) {
                                                        Color(0xFF155DFC)
                                                    } else {
                                                        Color(
                                                            0xFF374151,
                                                        )
                                                    },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { onIntent(EventIntent.OpenMapPicker) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = if (data.latitude != null) Color(0xFFF0FDF4) else Color.Transparent,
                                contentColor =
                                    if (data.latitude != null) {
                                        Color(0xFF15803D)
                                    } else {
                                        Color(
                                            0xFF4B5563,
                                        )
                                    },
                            ),
                        border = if (data.latitude != null) null else ButtonDefaults.outlinedButtonBorder,
                    ) {
                        Icon(
                            if (data.latitude != null) Icons.Default.CheckCircle else Icons.Default.Map,
                            null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (data.latitude != null) "Точка на карте установлена" else "Указать точку на карте")
                    }

                    if (data.latitude != null && data.address.isNotBlank()) {
                        Text(
                            data.address,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFF6B7280),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        )
                    }

                    DSButton(
                        text = "Сохранить изменения",
                        onClick = { onIntent(EventIntent.SaveEditorChanges) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (data.isMapPickerOpen) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .clickable(enabled = false) {},
                ) {
                    LocationPickerMap(
                        initialLatitude = data.latitude,
                        initialLongitude = data.longitude,
                        onDismiss = { onIntent(EventIntent.CloseMapPicker) },
                        onConfirm = { lat, lng -> onIntent(EventIntent.SetLocation(lat, lng)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusSegment(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF3F4F6)
    val borderColor = if (isSelected) Color(0xFF155DFC) else Color.Transparent
    val textColor = if (isSelected) Color(0xFF155DFC) else Color(0xFF374151)
    Box(
        modifier =
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = textColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
