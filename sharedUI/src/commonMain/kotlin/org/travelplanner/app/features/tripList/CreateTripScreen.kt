package org.travelplanner.app.features.tripList

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import org.travelplanner.app.core.ImagePicker
import org.travelplanner.app.core.TripUtils.toReadableDate
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSTextInput

class CreateTripScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<CreateTripScreenModel>()
        val state by screenModel.state.collectAsState()
        var showDatePicker by remember { mutableStateOf(false) }
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            screenModel.effect.collect { effect ->
                when (effect) {
                    is CreateTripEffect.NavigateBack -> navigator.pop()
                    is CreateTripEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Создание поездки") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            bottomBar = {
                DSButton(
                    text = "Далее",
                    onClick = {
                        screenModel.handleIntent(CreateTripIntent.SaveClicked)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                )
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .padding(paddingValues)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ImagePicker(onImagePicked = {
                    screenModel.handleIntent(
                        CreateTripIntent.PhotoSelected(
                            it,
                        ),
                    )
                }) { onClick ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFE5E7EB))
                                .clickable { onClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.photoBytes != null) {
                            AsyncImage(
                                model = state.photoBytes,
                                contentDescription = "Cover Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.AddAPhoto,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Добавить обложку", color = Color.Gray)
                            }
                        }
                    }
                }

                DSTextInput(
                    value = state.title,
                    onValueChange = { screenModel.handleIntent(CreateTripIntent.TitleChanged(it)) },
                    placeholder = "Например: Токио 2026",
                    label = "Название поездки",
                    isError = state.showErrors && state.titleError != null,
                    errorMessage = state.titleError,
                    modifier = Modifier.fillMaxWidth(),
                )

                DSTextInput(
                    value = state.destination,
                    onValueChange = {
                        screenModel.handleIntent(
                            CreateTripIntent.DestinationChanged(
                                it,
                            ),
                        )
                    },
                    placeholder = "Город, страна",
                    label = "Место назначения",
                    modifier = Modifier.fillMaxWidth(),
                )

                val datesHasError = state.showErrors && state.datesError != null
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DSTextInput(
                        value = state.startDate?.toReadableDate() ?: "",
                        onValueChange = {},
                        placeholder = "",
                        label = "Начало",
                        enabled = false,
                        readOnly = true,
                        isError = datesHasError,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, null)
                            }
                        },
                        modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                    )

                    DSTextInput(
                        value = state.endDate?.toReadableDate() ?: "",
                        onValueChange = {},
                        placeholder = "",
                        label = "Конец",
                        enabled = false,
                        readOnly = true,
                        isError = datesHasError,
                        modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                    )
                }
                if (datesHasError) {
                    Text(
                        text = state.datesError ?: "",
                        color = Color(0xFFD32F2F),
                        fontSize = 12.sp,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DSTextInput(
                        value = state.currency,
                        onValueChange = {
                            screenModel.handleIntent(
                                CreateTripIntent.CurrencyChanged(
                                    it,
                                ),
                            )
                        },
                        placeholder = "JPY",
                        label = "Валюта",
                        isError = state.showErrors && state.currencyError != null,
                        errorMessage = state.currencyError,
                        modifier = Modifier.weight(1f),
                    )
                    DSTextInput(
                        value = state.budget,
                        onValueChange = {
                            screenModel.handleIntent(
                                CreateTripIntent.BudgetChanged(
                                    it,
                                ),
                            )
                        },
                        placeholder = "0",
                        label = "Бюджет",
                        isError = state.showErrors && state.budgetError != null,
                        errorMessage = state.budgetError,
                        modifier = Modifier.weight(1f),
                    )
                }

                DSTextInput(
                    value = state.description,
                    onValueChange = {
                        screenModel.handleIntent(
                            CreateTripIntent.DescriptionChanged(
                                it,
                            ),
                        )
                    },
                    placeholder = "Краткое описание поездки...",
                    label = "Описание (опционально)",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 3,
                )
            }
        }

        if (showDatePicker) {
            val dateRangePickerState = rememberDateRangePickerState()
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            screenModel.handleIntent(CreateTripIntent.DatesChanged(start, end))
                        }
                        showDatePicker = false
                    }) { Text("ОК") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
                },
            ) {
                DateRangePicker(state = dateRangePickerState)
            }
        }
    }
}
