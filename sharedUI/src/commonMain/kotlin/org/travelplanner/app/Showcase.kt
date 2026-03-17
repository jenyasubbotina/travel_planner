package org.travelplanner.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.travelplanner.app.theme.AppColors
import org.travelplanner.app.theme.AppTypography
import org.travelplanner.app.theme.DSBadge
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSCustomChip
import org.travelplanner.app.theme.DSGradientCard
import org.travelplanner.app.theme.DSIconCard
import org.travelplanner.app.theme.DSNotificationBanner
import org.travelplanner.app.theme.DSProgressBar
import org.travelplanner.app.theme.DSSimpleCard
import org.travelplanner.app.theme.DSTextChip
import org.travelplanner.app.theme.DSTextInput
import org.travelplanner.app.theme.DSUserListItem
import org.travelplanner.app.theme.IconItem


@Preview(widthDp = 1600, heightDp = 2000)
@Composable
fun PreviewDesignSystem() {
    MaterialTheme(typography = AppTypography) {
        DesignSystemShowcase()
    }
}


@Composable
fun DesignSystemShowcase() {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F2))
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {

            DesignSection("Цветовая палитра") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorBox(AppColors.Primary, "Primary")
                    ColorBox(AppColors.Secondary, "Secondary")
                    ColorBox(AppColors.Success, "Success")
                    ColorBox(AppColors.Error, "Error")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorBox(AppColors.Warning, "Warning")
                    ColorBox(AppColors.Dark, "Dark")
                    ColorBox(AppColors.Gray, "Gray")
                    ColorBox(AppColors.Light, "Light")
                }
            }

            DesignSection("Chips / Фильтры") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DSTextChip("Активный", true, {})
                    DSTextChip("Неактивный", false, {})
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DSCustomChip(
                        "С иконкой",
                        AppColors.ChipTextInactive,
                        AppColors.ChipBgInactive,
                        icon = Icons.Outlined.Check
                    )
                    DSCustomChip(
                        "Жильё",
                        AppColors.TextSuccess,
                        AppColors.BgSuccessLight,
                        icon = Icons.Outlined.Home
                    )
                    DSCustomChip(
                        "Питание",
                        AppColors.TextWarning,
                        AppColors.BgWarningLight,
                        icon = Icons.Default.Fastfood
                    )
                }
            }

            DesignSection("Прогресс-бары") {
                DSProgressBar("Прогресс 36%", "¥127,500 / ¥350,000", 0.36f)
                Spacer(modifier = Modifier.height(16.dp))
                DSProgressBar("Прогресс 85%", "Почти завершено", 0.85f, color = AppColors.Warning)
                Spacer(modifier = Modifier.height(16.dp))
                DSProgressBar("Превышен бюджет", "105%", 1.05f, color = AppColors.Error)
                Spacer(modifier = Modifier.height(16.dp))
                DSProgressBar(
                    "Градиентный",
                    "50%",
                    0.5f,
                    isGradient = true,
                    gradientColors = listOf(AppColors.Success, AppColors.Primary)
                )
            }

            DesignSection("Оповещения и баннеры") {
                DSNotificationBanner(
                    title = "Информация",
                    text = "Это информационное сообщение для пользователя",
                    backgroundColor = AppColors.InfoBg,
                    borderColor = AppColors.InfoBorder,
                    contentColor = AppColors.InfoText,
                    iconColor = AppColors.InfoIcon,
                    icon = Icons.Outlined.Info
                )
                Spacer(modifier = Modifier.height(12.dp))

                DSNotificationBanner(
                    title = "Успешно",
                    text = "Операция выполнена успешно",
                    backgroundColor = AppColors.SuccessBg,
                    borderColor = AppColors.SuccessBorder,
                    contentColor = AppColors.SuccessText,
                    iconColor = AppColors.SuccessIcon,
                    icon = Icons.Outlined.CheckCircle
                )
                Spacer(modifier = Modifier.height(12.dp))

                DSNotificationBanner(
                    title = "Внимание",
                    text = "Требуется ваше внимание к этому пункту",
                    backgroundColor = AppColors.WarningBg,
                    borderColor = AppColors.WarningBorder,
                    contentColor = AppColors.WarningText,
                    iconColor = AppColors.WarningIcon,
                    icon = Icons.Outlined.Warning
                )
                Spacer(modifier = Modifier.height(12.dp))

                DSNotificationBanner(
                    title = "Ошибка",
                    text = "Произошла ошибка при выполнении операции",
                    backgroundColor = AppColors.ErrorBg,
                    borderColor = AppColors.ErrorBorder,
                    contentColor = AppColors.ErrorText,
                    iconColor = AppColors.ErrorIcon,
                    icon = Icons.Outlined.Close
                )
            }

        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {

            DesignSection("Типографика") {
                LabelText("Heading 1")
                Text("Заголовок первого уровня", style = AppTypography.headlineLarge)
                Spacer(modifier = Modifier.height(16.dp))

                LabelText("Heading 2")
                Text("Заголовок второго уровня", style = AppTypography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))

                LabelText("Heading 3")
                Text("Заголовок третьего уровня", style = AppTypography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                LabelText("Body")
                Text(
                    "Основной текст. Используется для описаний и контента.",
                    style = AppTypography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))

                LabelText("Caption")
                Text("Дополнительный текст меньшего размера", style = AppTypography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                LabelText("Overline")
                Text(
                    "МЕТКА / КАТЕГОРИЯ",
                    style = AppTypography.labelSmall.copy(letterSpacing = 1.sp)
                )
            }

            DesignSection("Карточки") {
                DSSimpleCard("Простая карточка", "Содержимое карточки с описанием")
                Spacer(modifier = Modifier.height(16.dp))
                DSIconCard(
                    "Карточка с иконкой",
                    "Описание с дополнительной информацией",
                    Icons.Default.LocationOn
                )
                Spacer(modifier = Modifier.height(16.dp))
                DSGradientCard("Градиентная карточка", "Используется для важных данных", "¥127,500")
            }


            Spacer(modifier = Modifier.height(24.dp))

            DesignSection("Списки") {
                DSUserListItem(
                    name = "Алексей Петров",
                    email = "alexey@example.com",
                    avatarChar = "А",
                    avatarColor = AppColors.AvatarBlue
                )
                Spacer(modifier = Modifier.height(8.dp))

                DSUserListItem(
                    name = "Мария Иванова",
                    email = "maria@example.com",
                    avatarChar = "М",
                    avatarColor = AppColors.AvatarPurple
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {

            DesignSection("Кнопки") {
                DSButton("Primary Button", {}, Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(12.dp))

                DSButton(
                    "Secondary Button",
                    {},
                    Modifier.fillMaxWidth(),
                    backgroundColor = AppColors.Secondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                DSButton("Outline Button", {}, Modifier.fillMaxWidth(), isOutline = true)

                Spacer(modifier = Modifier.height(12.dp))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = {}) {
                        Text(
                            "Text Button",
                            style = AppTypography.bodyLarge.copy(
                                color = AppColors.Primary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                DSButton("Disabled Button", {}, Modifier.fillMaxWidth(), enabled = false)

                Spacer(modifier = Modifier.height(12.dp))

                Row {
                    DSButton("С иконкой", {}, Modifier.weight(1f), icon = Icons.Default.Add)
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {},
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
            }

            DesignSection("Поля ввода") {
                DSTextInput("", {}, "Введите текст...", label = "Обычное поле")
                Spacer(modifier = Modifier.height(16.dp))
                DSTextInput("", {}, "Поиск...", label = "С иконкой", icon = Icons.Default.Search)
                Spacer(modifier = Modifier.height(16.dp))
                DSTextInput(
                    "Некорректное значение",
                    {},
                    "",
                    label = "С ошибкой",
                    isError = true,
                    errorMessage = "Это поле обязательно"
                )
                Spacer(modifier = Modifier.height(16.dp))
                DSTextInput(
                    "",
                    {},
                    "Введите описание...",
                    label = "Текстовая область",
                    singleLine = false,
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(16.dp))
                DSTextInput("", {}, label = "Выпадающий список", placeholder = "")
            }

            DesignSection("Иконки") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconItem(Icons.Default.Search, "Search")
                    IconItem(Icons.Default.DateRange, "Calendar")
                    IconItem(Icons.Default.LocationOn, "MapPin")
                    IconItem(Icons.Default.Person, "Users")
                    IconItem(Icons.Default.TrendingUp, "Trending")
                    IconItem(Icons.Default.Notifications, "Bell")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    IconItem(Icons.Default.FilterList, "Filter")
                    IconItem(Icons.Default.Download, "Download")
                    IconItem(Icons.Default.Share, "Share")
                }
            }

            DesignSection("Значки (Badges)") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DSBadge("Новое", AppColors.TextBlue, AppColors.BgBlueLight)

                    DSBadge("Активно", Color.White, AppColors.Success)

                    DSBadge("Ожидание", AppColors.TextWarning, AppColors.BgWarningLight)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DSBadge("Завершено", Color.White, AppColors.TextTertiary)

                    DSBadge("Отменено", Color(0xFFC10007), Color(0xFFFFE2E2))
                }
            }
        }
    }
}

@Composable
fun LabelText(text: String) {
    Text(text, style = AppTypography.labelSmall, color = AppColors.TextSecondary)
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
fun ColorBox(color: Color, name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 74.dp, height = 64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(color)
                .border(
                    width = if (color == AppColors.Light || color == Color.White) 1.dp else 0.dp,
                    color = AppColors.Border,
                    shape = RoundedCornerShape(16.dp)
                )
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(name, style = AppTypography.labelSmall, color = AppColors.TextPrimary)
    }
}

@Composable
fun DesignSection(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = AppTypography.titleMedium)
            Spacer(modifier = Modifier.height(20.dp))
            content()
        }
    }
}
