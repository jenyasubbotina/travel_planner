package org.travelplanner.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.travelplanner.app.theme.AppColors
import org.travelplanner.app.theme.AppTypography
import org.travelplanner.app.theme.DSButton

@Composable
fun DSEmptyStateCard(
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color(0xFF99A1AF),
    iconBgColor: Color = AppColors.Light,
    isIconCircleVisible: Boolean = true,
    customIconContent: (@Composable () -> Unit)? = null,
    buttonColor: Color = AppColors.Primary,
    buttonTextColor: Color = Color.White,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (customIconContent != null) {
                customIconContent()
            } else if (icon != null) {
                if (isIconCircleVisible) {
                    Box(
                        modifier =
                            Modifier
                                .size(96.dp)
                                .background(iconBgColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(60.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style =
                    AppTypography.titleLarge.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                textAlign = TextAlign.Center,
                color = AppColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style =
                    AppTypography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = AppColors.TextSecondary,
                    ),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            DSButton(
                text = buttonText,
                onClick = onButtonClick,
                backgroundColor = buttonColor,
                contentColor = buttonTextColor,
                modifier = Modifier.wrapContentWidth(),
            )
        }
    }
}

@Preview(heightDp = 3000)
@Composable
fun EmptyStatesScreen() {
    val scrollState = rememberScrollState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF9FAFB))
                .verticalScroll(scrollState)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Пустые состояния (Empty States)",
            style = AppTypography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        DSEmptyStateCard(
            title = "Нет поездок",
            description = "Создайте первую поездку и начните планировать незабываемое путешествие",
            buttonText = "Создать поездку",
            onButtonClick = {},
            icon = Icons.Default.LocationOn,
        )

        DSEmptyStateCard(
            title = "Нет расходов",
            description = "Добавьте первый расход, чтобы начать отслеживание бюджета",
            buttonText = "Добавить расход",
            onButtonClick = {},
            icon = Icons.Default.Receipt,
        )

        DSEmptyStateCard(
            title = "Только вы в поездке",
            description = "Пригласите друзей, чтобы делить расходы и планировать вместе",
            buttonText = "Пригласить участников",
            onButtonClick = {},
            icon = Icons.Default.Group,
        )

        DSEmptyStateCard(
            title = "Нет файлов",
            description = "Загрузите брони, билеты и чеки, чтобы всё было под рукой",
            buttonText = "Загрузить файл",
            onButtonClick = {},
            icon = Icons.Default.Description,
        )

        DSEmptyStateCard(
            title = "Нет подключения",
            description = "Проверьте интернет-соединение. Изменения синхронизируются автоматически",
            buttonText = "Повторить попытку",
            onButtonClick = {},
            icon = Icons.Default.Wifi,
            iconBgColor = AppColors.BgWarningLight,
            iconTint = AppColors.Warning,
            buttonColor = AppColors.Warning,
        )

        DSEmptyStateCard(
            title = "Ничего не найдено",
            description = "Попробуйте изменить параметры поиска или очистите фильтры",
            buttonText = "Сбросить фильтры",
            onButtonClick = {},
            icon = Icons.Default.Search,
            iconTint = AppColors.TextPrimary,
            isIconCircleVisible = false,
            buttonColor = AppColors.Light,
            buttonTextColor = AppColors.ChipTextInactive,
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}
