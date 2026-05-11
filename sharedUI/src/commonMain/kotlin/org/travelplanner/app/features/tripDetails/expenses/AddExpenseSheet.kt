package org.travelplanner.app.features.tripDetails.expenses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.travelplanner.app.core.ImagePicker
import org.travelplanner.app.core.rememberResolvedImageUrl
import org.travelplanner.app.domain.Participant
import org.travelplanner.app.theme.AppColors
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSTextInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormSheet(
    screenModel: ExpenseFormScreenModel,
    onDismiss: () -> Unit,
    onSuccess: (message: String?) -> Unit,
    onError: (message: String) -> Unit = {},
) {
    val state by screenModel.state.collectAsState()
    val scrollState = rememberScrollState()
    val isSolo = state.participants.size == 1

    LaunchedEffect(Unit) {
        screenModel.effect.collect { effect ->
            when (effect) {
                is ExpenseFormEffect.SaveSuccess -> {
                    onSuccess(null)
                }

                is ExpenseFormEffect.SaveQueuedForApproval -> {
                    onSuccess("Предложение отправлено создателю на подтверждение")
                }

                is ExpenseFormEffect.SaveBlockedAnotherPending -> {
                    onSuccess("Другой участник уже предложил изменения. Дождитесь решения создателя.")
                }

                is ExpenseFormEffect.ShowError -> {
                    onError(effect.message)
                }
            }
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.date)

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    screenModel.handleIntent(ExpenseFormIntent.DateChanged(datePickerState.selectedDateMillis))
                    showDatePicker = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Добавить расход",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = AppColors.TextPrimary,
                )
            }
        }
        Divider(color = AppColors.Border, thickness = 1.dp)

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InputLabel("Сумма")
            val amountHasError = state.showErrors && state.amountError != null
            OutlinedTextField(
                value = state.amount,
                onValueChange = { screenModel.handleIntent(ExpenseFormIntent.AmountChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                isError = amountHasError,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = AppColors.Border,
                        unfocusedIndicatorColor = AppColors.Border,
                        errorIndicatorColor = AppColors.Error,
                        errorContainerColor = Color.White,
                    ),
                textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, color = Color.Black),
                trailingIcon = {
                    Text(
                        state.currency,
                        fontSize = 22.sp,
                        color = AppColors.Gray,
                    )
                },
                placeholder = {
                    Text(
                        "0",
                        fontSize = 22.sp,
                        color = AppColors.TextPrimary.copy(alpha = 0.5f),
                    )
                },
                singleLine = true,
            )
            if (amountHasError) {
                Text(
                    text = state.amountError ?: "",
                    color = AppColors.Error,
                    fontSize = 12.sp,
                )
            }

            InputLabel("Категория")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryButton(
                        "HOUSING",
                        "🏠",
                        "Жильё",
                        state.category,
                    ) { screenModel.handleIntent(ExpenseFormIntent.CategoryChanged(it)) }
                    CategoryButton(
                        "FOOD",
                        "🍱",
                        "Питание",
                        state.category,
                    ) { screenModel.handleIntent(ExpenseFormIntent.CategoryChanged(it)) }
                    CategoryButton(
                        "TRANSPORT",
                        "🚇",
                        "Транспорт",
                        state.category,
                    ) { screenModel.handleIntent(ExpenseFormIntent.CategoryChanged(it)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryButton(
                        "FUN",
                        "🎭",
                        "Развлечения",
                        state.category,
                    ) { screenModel.handleIntent(ExpenseFormIntent.CategoryChanged(it)) }
                    CategoryButton(
                        "SHOPPING",
                        "🛍️",
                        "Покупки",
                        state.category,
                    ) { screenModel.handleIntent(ExpenseFormIntent.CategoryChanged(it)) }
                    CategoryButton(
                        "OTHER",
                        "📦",
                        "Другое",
                        state.category,
                    ) { screenModel.handleIntent(ExpenseFormIntent.CategoryChanged(it)) }
                }
            }

            InputLabel("Описание")
            DSTextInput(
                value = state.description,
                onValueChange = { screenModel.handleIntent(ExpenseFormIntent.DescriptionChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "На что потрачено?",
                isError = state.showErrors && state.descriptionError != null,
                errorMessage = state.descriptionError,
            )

            DSTextInput(
                value = screenModel.getFormattedDate(),
                onValueChange = {},
                placeholder = "",
                label = "Дата",
                enabled = false,
                readOnly = true,
                icon = Icons.Default.CalendarToday,
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
            )

            if (!isSolo) {
                InputLabel("Кто оплатил")
                PayerDropdown(
                    participants = state.participants.map { it.participant },
                    selectedId = state.payerId,
                    onSelect = { screenModel.handleIntent(ExpenseFormIntent.PayerChanged(it)) },
                )
                if (state.showErrors && state.payerError != null) {
                    Text(
                        text = state.payerError ?: "",
                        color = AppColors.Error,
                        fontSize = 12.sp,
                    )
                }

                InputLabel("Как разделить")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SplitMethodButton(
                            text = "Поровну",
                            selected = state.splitMethod == SplitMethod.EQUAL,
                            onClick = {
                                screenModel.handleIntent(
                                    ExpenseFormIntent.SplitMethodChanged(SplitMethod.EQUAL),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SplitMethodButton(
                            text = "Вручную",
                            selected = state.splitMethod == SplitMethod.EXACT_AMOUNT,
                            onClick = {
                                screenModel.handleIntent(
                                    ExpenseFormIntent.SplitMethodChanged(SplitMethod.EXACT_AMOUNT),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SplitMethodButton(
                            text = "Проценты",
                            selected = state.splitMethod == SplitMethod.PERCENTAGE,
                            onClick = {
                                screenModel.handleIntent(
                                    ExpenseFormIntent.SplitMethodChanged(SplitMethod.PERCENTAGE),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SplitMethodButton(
                            text = "Доли",
                            selected = state.splitMethod == SplitMethod.SHARES,
                            onClick = {
                                screenModel.handleIntent(
                                    ExpenseFormIntent.SplitMethodChanged(SplitMethod.SHARES),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(AppColors.CardHeaderBg, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Разделить между (${state.participants.size})",
                            fontSize = 14.sp,
                            color = AppColors.TextTertiary,
                        )
                        state.participants.forEach { item ->
                            ParticipantRow(
                                item = item,
                                displayAmount = "${state.currency}${item.calculatedAmount.toInt()}",
                                currency = state.currency,
                                splitMethod = state.splitMethod,
                                onToggle = {
                                    screenModel.handleIntent(
                                        ExpenseFormIntent.ParticipantToggled(
                                            item.participant.id,
                                        ),
                                    )
                                },
                                onInputValue = { valStr ->
                                    screenModel.handleIntent(
                                        ExpenseFormIntent.InputValueChanged(
                                            item.participant.id,
                                            valStr,
                                        ),
                                    )
                                },
                            )
                        }
                        val participantsMessage = state.participantsError ?: state.splitError
                        if (participantsMessage != null && (state.showErrors || state.splitMethod != SplitMethod.EQUAL)) {
                            Text(
                                text = participantsMessage,
                                color = AppColors.Error,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            InputLabel("Чек (опционально)")
            val hasPhoto = state.photoBytes != null || state.imageUrl != null
            ImagePicker(onImagePicked = {
                screenModel.handleIntent(
                    ExpenseFormIntent.PhotoSelected(
                        it,
                    ),
                )
            }) { onClick ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.CardHeaderBg)
                            .border(
                                BorderStroke(1.dp, AppColors.Gray),
                                RoundedCornerShape(16.dp),
                            ).clickable { onClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        state.photoBytes != null -> {
                            AsyncImage(
                                model = state.photoBytes,
                                contentDescription = "Receipt Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        state.imageUrl != null -> {
                            val resolved = rememberResolvedImageUrl(state.imageUrl)
                            AsyncImage(
                                model = resolved,
                                contentDescription = "Receipt Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        else -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Description,
                                    null,
                                    tint = AppColors.TextTertiary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    "Загрузить фото чека",
                                    color = AppColors.TextTertiary,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }

                    if (hasPhoto) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable {
                                        screenModel.handleIntent(
                                            ExpenseFormIntent.PhotoSelected(null),
                                        )
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Удалить фото",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DSButton(
                text = "Отмена",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                isOutline = true,
            )

            DSButton(
                text = "Добавить",
                onClick = { screenModel.handleIntent(ExpenseFormIntent.Save) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun InputLabel(text: String) {
    Text(text, fontSize = 14.sp, color = AppColors.TextTertiary)
}

@Composable
fun RowScope.CategoryButton(
    id: String,
    emoji: String,
    label: String,
    activeId: String,
    onClick: (String) -> Unit,
) {
    val isSelected = id == activeId
    val borderColor = if (isSelected) AppColors.Primary else AppColors.Border

    Box(
        modifier =
            Modifier
                .weight(1f)
                .height(64.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
                .clickable { onClick(id) }
                .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 12.sp, color = AppColors.TextPrimary, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayerDropdown(
    participants: List<Participant>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = participants.find { it.id == selectedId }?.name ?: "Выберите..."

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = AppColors.Border,
                    unfocusedIndicatorColor = AppColors.Border,
                ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color.White),
        ) {
            participants.forEach { participant ->
                DropdownMenuItem(
                    text = { Text(participant.name) },
                    onClick = {
                        onSelect(participant.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun SplitMethodButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val bgColor = if (selected) AppColors.InfoBg else Color.White
    val borderColor = if (selected) AppColors.Primary else AppColors.Border
    val textColor = if (selected) AppColors.Primary else AppColors.TextPrimary

    Box(
        modifier =
            modifier
                .height(46.dp)
                .background(bgColor, RoundedCornerShape(16.dp))
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontSize = 15.sp)
    }
}

@Composable
fun ParticipantRow(
    item: ParticipantSplitState,
    displayAmount: String,
    currency: String,
    splitMethod: SplitMethod,
    onToggle: () -> Unit,
    onInputValue: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .weight(1f)
                    .clickable { onToggle() },
        ) {
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = AppColors.Primary),
            )
            Spacer(Modifier.width(8.dp))
            Text(item.participant.name, fontSize = 16.sp, color = AppColors.TextPrimary)
        }

        when (splitMethod) {
            SplitMethod.EQUAL -> {
                val text = if (item.isSelected) displayAmount else "—"
                Text(text, fontSize = 14.sp, color = AppColors.TextSecondary)
            }

            SplitMethod.EXACT_AMOUNT -> {
                SplitInputField(
                    value = item.inputValue,
                    isSelected = item.isSelected,
                    suffix = currency,
                    onValueChange = onInputValue,
                )
            }

            SplitMethod.PERCENTAGE -> {
                Column(horizontalAlignment = Alignment.End) {
                    SplitInputField(
                        value = item.inputValue,
                        isSelected = item.isSelected,
                        suffix = "%",
                        onValueChange = onInputValue,
                    )
                    if (item.isSelected) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "$currency${item.calculatedAmount.toInt()}",
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary,
                        )
                    }
                }
            }

            SplitMethod.SHARES -> {
                Column(horizontalAlignment = Alignment.End) {
                    SplitInputField(
                        value = item.inputValue,
                        isSelected = item.isSelected,
                        suffix = "доли",
                        onValueChange = onInputValue,
                    )
                    if (item.isSelected) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "$currency${item.calculatedAmount.toInt()}",
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitInputField(
    value: String,
    isSelected: Boolean,
    suffix: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(1.dp, AppColors.Border, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle =
                TextStyle(
                    fontSize = 16.sp,
                    color = if (isSelected) AppColors.Primary else Color.Gray,
                    textAlign = TextAlign.End,
                ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(56.dp),
            singleLine = true,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            suffix,
            fontSize = 14.sp,
            color = AppColors.Gray,
        )
    }
}
