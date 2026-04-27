package org.travelplanner.app.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DSButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppColors.Primary,
    contentColor: Color = Color.White,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isOutline: Boolean = false,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)

    when {
        isLoading -> {
            Button(
                onClick = {},
                modifier = modifier.height(48.dp),
                shape = shape,
                enabled = false,
                colors =
                    ButtonDefaults.buttonColors(
                        disabledContainerColor = backgroundColor,
                        disabledContentColor = contentColor,
                    ),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = contentColor,
                    strokeWidth = 2.5.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Загрузка...",
                    style =
                        AppTypography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = contentColor,
                        ),
                )
            }
        }

        isSuccess -> {
            Button(
                onClick = onClick,
                modifier = modifier.height(48.dp),
                shape = shape,
                enabled = true,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AppColors.ButtonSuccess,
                        contentColor = Color.White,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    style =
                        AppTypography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                        ),
                )
            }
        }

        isOutline -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.height(48.dp),
                shape = shape,
                enabled = enabled,
                border = null,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextTertiary,
                        containerColor = AppColors.ChipBgInactive,
                        disabledContentColor = AppColors.Gray,
                    ),
            ) {
                Text(
                    text = text,
                    style =
                        AppTypography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = AppColors.ChipTextInactive,
                        ),
                )
            }
        }

        !enabled -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.height(48.dp),
                shape = shape,
                enabled = enabled,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.Gray,
                        containerColor = AppColors.Gray,
                        disabledContentColor = AppColors.Gray,
                        disabledContainerColor = AppColors.Gray,
                    ),
            ) {
                Text(
                    text = text,
                    style =
                        AppTypography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = AppColors.TextSecondary,
                        ),
                )
            }
        }

        else -> {
            Button(
                onClick = onClick,
                modifier = modifier.height(48.dp),
                shape = shape,
                enabled = enabled,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (enabled) backgroundColor else AppColors.Gray,
                        contentColor = if (enabled) contentColor else AppColors.TextSecondary,
                    ),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style =
                        AppTypography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = contentColor,
                        ),
                )
            }
        }
    }
}

@Composable
fun DSTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(text = label, style = AppTypography.bodyMedium, color = AppColors.TextTertiary)
            Spacer(modifier = Modifier.height(6.dp))
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = AppColors.Gray) },
            leadingIcon =
                if (icon != null) {
                    { Icon(icon, contentDescription = null, tint = AppColors.TextSecondary) }
                } else {
                    null
                },
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(16.dp),
            singleLine = singleLine,
            minLines = minLines,
            isError = isError,
            enabled = enabled,
            readOnly = readOnly,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    errorContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    disabledTextColor = Color.Black,
                    disabledBorderColor = AppColors.Border,
                    focusedBorderColor = if (isError) AppColors.Error else AppColors.Primary,
                    unfocusedBorderColor = if (isError) AppColors.Error else AppColors.Border,
                    errorBorderColor = AppColors.Error,
                ),
        )

        if (isError && errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = errorMessage, style = AppTypography.labelSmall, color = AppColors.Error)
        }
    }
}

@Composable
fun DSSimpleCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(AppColors.CardHeaderBg)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Text(title, style = AppTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            }
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(description, style = AppTypography.bodyMedium)
            }
        }
    }
}

@Composable
fun DSIconCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
                .clickable { },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppColors.BgBlueLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = AppTypography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, style = AppTypography.bodyMedium)
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColors.Gray,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

@Composable
fun DSGradientCard(
    title: String,
    description: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush =
                        Brush.linearGradient(
                            colors = listOf(AppColors.Primary, AppColors.Secondary),
                        ),
                ).padding(20.dp),
    ) {
        Column {
            Text(title, style = AppTypography.titleLarge, color = Color.White)
            Text(
                description,
                style = AppTypography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(value, style = AppTypography.headlineMedium, color = Color.White)
        }
    }
}

@Composable
fun DSProgressBar(
    label: String,
    valueDisplay: String,
    progress: Float,
    color: Color = AppColors.Primary,
    modifier: Modifier = Modifier,
    isGradient: Boolean = false,
    gradientColors: List<Color> = emptyList(),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = AppTypography.bodyMedium, color = AppColors.TextPrimary)
            Text(
                text = valueDisplay,
                style = AppTypography.bodyMedium,
                color = if (progress > 1f) AppColors.Error else AppColors.TextSecondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(AppColors.Light),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = progress.coerceAtMost(1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .then(
                            if (isGradient) {
                                Modifier.background(Brush.horizontalGradient(gradientColors))
                            } else {
                                Modifier.background(color)
                            },
                        ),
            )
        }
    }
}

@Composable
fun DSCustomChip(
    text: String,
    textColor: Color,
    backgroundColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = backgroundColor,
        shape = CircleShape,
        modifier = modifier.height(32.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text,
                color = textColor,
                style = AppTypography.bodyMedium,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
fun DSTextChip(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
) {
    Surface(
        color = if (isActive) AppColors.Primary else AppColors.ChipBgInactive,
        shape = CircleShape,
        modifier = Modifier.height(32.dp).clickable { onClick() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxHeight(),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isActive) Color.White else AppColors.ChipTextInactive,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = text,
                style = AppTypography.bodyMedium,
                color = if (isActive) Color.White else AppColors.ChipTextInactive,
            )
        }
    }
}

@Composable
fun DSBadge(
    text: String,
    textColor: Color,
    backgroundColor: Color,
) {
    Surface(
        color = backgroundColor,
        shape = CircleShape,
        modifier = Modifier.height(24.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        ) {
            Text(
                text = text,
                color = textColor,
                style = AppTypography.labelSmall,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
fun IconItem(
    icon: ImageVector,
    name: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.TextPrimary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(name, style = AppTypography.labelSmall)
    }
}

@Composable
fun DSNotificationBanner(
    title: String,
    text: String,
    backgroundColor: Color,
    borderColor: Color,
    contentColor: Color,
    iconColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .border(0.7.dp, borderColor, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style =
                        AppTypography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        ),
                    color = contentColor,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = text,
                    style =
                        AppTypography.bodyMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp,
                        ),
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
fun DSUserListItem(
    name: String,
    email: String,
    avatarChar: String,
    avatarColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = AppColors.ListItemBackground,
        shape = RoundedCornerShape(16.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable { },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(avatarColor, CircleShape),
            ) {
                Text(
                    text = avatarChar,
                    color = Color.White,
                    style = AppTypography.bodyLarge.copy(fontSize = 16.sp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style =
                        AppTypography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = AppColors.TextPrimary,
                        ),
                )
                Text(
                    text = email,
                    style =
                        AppTypography.labelSmall.copy(
                            color = AppColors.TextSecondary,
                        ),
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFF99A1AF),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
fun DSSkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp,
    width: Dp = Dp.Unspecified,
    cornerRadius: Dp = 4.dp,
) {
    Box(
        modifier =
            modifier
                .height(height)
                .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier.fillMaxWidth())
                .background(AppColors.SkeletonColor, RoundedCornerShape(cornerRadius)),
    )
}

@Composable
fun DSSkeletonListItem() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .background(AppColors.SkeletonColor, CircleShape),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            DSSkeletonBlock(height = 16.dp, width = 122.dp)
            Spacer(modifier = Modifier.height(8.dp))
            DSSkeletonBlock(height = 12.dp, width = 82.dp)
        }
    }
}

@Composable
fun DSThreeDotsLoader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val dotColor = AppColors.Primary
        Box(modifier = Modifier.size(12.dp).background(dotColor, CircleShape))
        Box(modifier = Modifier.size(12.dp).background(dotColor, CircleShape))
        Box(modifier = Modifier.size(12.dp).background(dotColor, CircleShape))
    }
}

@Composable
fun DSLoadingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(Color(0xFFF9FAFB), RoundedCornerShape(16.dp)),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = AppColors.Border,
                    strokeWidth = 4.dp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Загрузка данных...",
                    style = AppTypography.bodyMedium,
                    color = AppColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
fun DSPullToRefreshMock() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(AppColors.CardHeaderBg, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .border(2.dp, AppColors.Gray, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { 0.25f },
                    modifier = Modifier.size(40.dp),
                    color = AppColors.Gray,
                    strokeWidth = 2.dp,
                    trackColor = Color.Transparent,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Обновление...", style = AppTypography.bodyMedium, color = AppColors.TextTertiary)
        }
    }
}
