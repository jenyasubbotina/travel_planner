package org.travelplanner.app.features.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.travelplanner.app.core.AppUser
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.features.tripList.TripListScreen

class ProfileScreenModel(
    private val userSession: UserSession,
) : ScreenModel {
    val currentUser =
        userSession.currentUser
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), null)

    val availableUsers =
        userSession.availableUsers
            .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logout() {
        screenModelScope.launch { userSession.logout() }
    }

    fun switchUser(userId: String) {
        userSession.switchUser(userId)
    }
}

class ProfileScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val screenModel = getScreenModel<ProfileScreenModel>()
        val navigator = LocalNavigator.currentOrThrow
        val currentUser by screenModel.currentUser.collectAsState()
        val availableUsers by screenModel.availableUsers.collectAsState()

        val userName = currentUser?.name ?: "Ваше Имя"
        val userEmail = currentUser?.email ?: "you@example.com"
        val otherAccounts = availableUsers.filter { it.id != currentUser?.id }

        var isDarkMode by remember { mutableStateOf(false) }
        var isPushEnabled by remember { mutableStateOf(false) }
        var isEmailEnabled by remember { mutableStateOf(true) }
        var isRemindersEnabled by remember { mutableStateOf(false) }
        var isOfflineMode by remember { mutableStateOf(true) }

        val backgroundColor = Color(0xFFF9FAFB)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Профиль") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    SectionHeader("АККАУНТ")
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GradientAvatar(
                                seed = (currentUser?.id ?: "") + userName,
                                initials = avatarInitials(userName),
                                avatarUrl = currentUser?.avatarUrl,
                                size = 64.dp,
                                fontSize = 24.sp,
                                showBorder = false,
                            )
                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    userName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827),
                                )
                                Text(userEmail, fontSize = 14.sp, color = Color(0xFF6B7280))
                            }

                            TextButton(onClick = { }) {
                                Text("Изменить", color = Color(0xFF155DFC), fontSize = 14.sp)
                            }
                        }
                    }
                }

                if (otherAccounts.isNotEmpty()) {
                    item {
                        SectionHeader("ПЕРЕКЛЮЧЕНИЕ АККАУНТА")
                        Spacer(modifier = Modifier.height(8.dp))
                        CardSettingsGroup {
                            otherAccounts.forEachIndexed { index, account ->
                                AccountSwitchRow(
                                    user = account,
                                    onClick = {
                                        screenModel.switchUser(account.id)
                                        navigator.popUntil { it is TripListScreen }
                                    },
                                )
                                if (index < otherAccounts.size - 1) {
                                    DividerItem()
                                }
                            }
                            DividerItem()
                            SettingsActionRow(
                                icon = Icons.Outlined.PersonAdd,
                                title = "Добавить аккаунт",
                                subtitle = "Войти с другим email",
                                onClick = { screenModel.logout() },
                            )
                        }
                    }
                }

                item {
                    SectionHeader("ОСНОВНЫЕ")
                    Spacer(modifier = Modifier.height(8.dp))
                    CardSettingsGroup {
                        SettingsActionRow(
                            icon = Icons.Outlined.Language,
                            title = "Язык",
                            subtitle = "Русский",
                            onClick = { },
                        )
                        DividerItem()
                        SettingsActionRow(
                            icon = Icons.Outlined.Public,
                            title = "Валюта по умолчанию",
                            subtitle = "¥ Юани",
                            onClick = { },
                        )
                        DividerItem()
                        SettingsToggleRow(
                            icon = Icons.Outlined.DarkMode,
                            title = "Тёмная тема",
                            subtitle = "Автоматически",
                            isChecked = isDarkMode,
                            onCheckedChange = { isDarkMode = it },
                        )
                    }
                }

                item {
                    SectionHeader("УВЕДОМЛЕНИЯ")
                    Spacer(modifier = Modifier.height(8.dp))
                    CardSettingsGroup {
                        SettingsToggleRow(
                            icon = Icons.Outlined.Notifications,
                            title = "Push-уведомления",
                            subtitle = "О новых расходах и событиях",
                            isChecked = isPushEnabled,
                            onCheckedChange = { isPushEnabled = it },
                        )
                        DividerItem()
                        SettingsToggleRow(
                            icon = Icons.Outlined.Email,
                            title = "Email-уведомления",
                            subtitle = "Еженедельная сводка",
                            isChecked = isEmailEnabled,
                            onCheckedChange = { isEmailEnabled = it },
                        )
                        DividerItem()
                        SettingsToggleRow(
                            icon = Icons.Outlined.NotificationsActive,
                            title = "Напоминания о долгах",
                            subtitle = "Раз в неделю",
                            isChecked = isRemindersEnabled,
                            onCheckedChange = { isRemindersEnabled = it },
                        )
                    }
                }

                item {
                    SectionHeader("ДАННЫЕ")
                    Spacer(modifier = Modifier.height(8.dp))
                    CardSettingsGroup {
                        SettingsToggleRow(
                            icon = Icons.Outlined.DownloadForOffline,
                            title = "Офлайн-режим",
                            subtitle = "Работа без интернета",
                            isChecked = isOfflineMode,
                            onCheckedChange = { isOfflineMode = it },
                        )
                    }
                }

                item {
                    SectionHeader("ПОДДЕРЖКА")
                    Spacer(modifier = Modifier.height(8.dp))
                    CardSettingsGroup {
                        SettingsActionRow(
                            icon = Icons.Outlined.HelpOutline,
                            title = "Помощь и FAQ",
                            onClick = { },
                        )
                        DividerItem()
                        SettingsActionRow(
                            icon = Icons.Outlined.Info,
                            title = "О приложении",
                            subtitle = "Версия 1.0.0",
                            onClick = { },
                        )
                    }
                }

                item {
                    SectionHeader("СЕССИЯ")
                    Spacer(modifier = Modifier.height(8.dp))
                    CardSettingsGroup {
                        SettingsActionRow(
                            icon = Icons.AutoMirrored.Outlined.Logout,
                            title = "Выйти из аккаунта",
                            subtitle = "Вы сможете войти снова на этом устройстве",
                            onClick = { screenModel.logout() },
                        )
                    }
                }

                item {
                    SectionHeader("ОПАСНАЯ ЗОНА", color = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SettingsActionRow(
                            icon = Icons.Outlined.DeleteOutline,
                            title = "Удалить аккаунт",
                            subtitle = "Все данные будут безвозвратно удалены",
                            iconTint = Color(0xFFDC2626),
                            titleColor = Color(0xFFDC2626),
                            subtitleColor = Color(0xFFDC2626),
                            onClick = { },
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Travel Planner v1.0.0\nMade with ❤️ for travelers",
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            color = Color(0xFF6B7280),
                            lineHeight = 20.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    color: Color = Color(0xFF6B7280),
) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
fun CardSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
fun DividerItem() {
    Divider(
        color = Color(0xFFF3F4F6),
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = Color(0xFF374151),
    titleColor: Color = Color(0xFF111827),
    subtitleColor: Color = Color(0xFF6B7280),
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = titleColor)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 13.sp, color = subtitleColor)
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (iconTint == Color(0xFFDC2626)) iconTint else Color(0xFF9CA3AF),
        )
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!isChecked) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF374151),
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 13.sp, color = Color(0xFF6B7280))
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF155DFC),
                    uncheckedThumbColor = Color.White,
                    uncheckedBorderColor = Color.Transparent,
                    uncheckedTrackColor = Color(0xFFD1D5DB),
                ),
        )
    }
}

@Composable
private fun AccountSwitchRow(
    user: AppUser,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientAvatar(
            seed = user.id + user.name,
            initials = avatarInitials(user.name),
            avatarUrl = user.avatarUrl,
            size = 40.dp,
            fontSize = 14.sp,
            showBorder = false,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                user.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF111827),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(user.email, fontSize = 13.sp, color = Color(0xFF6B7280))
        }
        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = null,
            tint = Color(0xFF9CA3AF),
        )
    }
}
