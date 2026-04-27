package org.travelplanner.app.features.tripDetails.more

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.core.BackendFeatureFlags
import org.travelplanner.app.core.FilePicker
import org.travelplanner.app.core.rememberFileDownloader
import org.travelplanner.app.domain.MediaType
import org.travelplanner.app.domain.TripMediaItem
import org.travelplanner.app.features.profile.ui.GradientAvatar
import org.travelplanner.app.features.profile.ui.avatarInitials
import org.travelplanner.app.features.tripDetails.history.ui.HistoryScreen
import org.travelplanner.app.features.tripDetails.more.checklist.ui.ChecklistScreen
import org.travelplanner.app.features.tripDetails.more.files.ui.FilesScreen
import org.travelplanner.app.features.tripDetails.more.participants.ui.ParticipantsScreen
import org.travelplanner.app.theme.DSButton

data class MoreTab(
    private val tripId: String,
) : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.MoreHoriz)
            return remember { TabOptions(index = 4u, title = "Ещё", icon = icon) }
        }

    @Composable
    override fun Content() {
        val parentNavigator = LocalNavigator.currentOrThrow.parent!!
        val screenModel =
            parentNavigator.rememberNavigatorScreenModel<MoreTabScreenModel>(tag = tripId) {
                GlobalContext.get().get<MoreTabScreenModel> { parametersOf(tripId) }
            }

        val state by screenModel.state.collectAsState()

        val isOwner = state.currentUser?.id.toString() == state.trip?.ownerUserId
        var showDeleteWarning by remember { mutableStateOf(false) }
        var downloadTarget by remember { mutableStateOf<TripMediaItem?>(null) }
        val downloadFile = rememberFileDownloader()
        val coroutineScope = rememberCoroutineScope()

        val backgroundColor = Color(0xFFF9FAFB)

        val activeCount = state.list.count { it.role != "LEFT" }

        val totalChecklist = state.checklist.size
        val completedChecklist =
            state.checklist.count { item ->
                val completedList =
                    item.completedBy

                if (item.isGroup) {
                    completedList.size >= state.list.size
                } else {
                    completedList.contains(
                        state.currentUser?.id.toString(),
                    )
                }
            }

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
        ) {
            item {
                val totalCount = activeCount + state.pendingRequests.size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Участники",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier =
                                Modifier
                                    .background(Color(0xFFF3F4F6), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(totalCount.toString(), fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    DSButton(
                        text = "Пригласить",
                        onClick = {
                            // TODO(server): once FCM invitation push lands, always route to ShowInviteEmailDialog
                            // (or revert to ShowAddDialog if a join-code flow is preferred).
                            val intent =
                                if (BackendFeatureFlags.JOIN_BY_CODE_ENABLED) {
                                    MoreTabIntent.ShowAddDialog
                                } else {
                                    MoreTabIntent.ShowInviteEmailDialog
                                }
                            screenModel.handleIntent(intent)
                        },
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        state.list.sortedBy { it.role == "LEFT" }.forEach { participant ->
                            ParticipantRowDetailed(
                                name = if (participant.userId == state.currentUser?.id.toString()) "Вы" else participant.name,
                                email = participant.email,
                                role = participant.role,
                                userId = participant.userId,
                                avatarUrl = participant.avatarUrl,
                            )
                        }

                        if (state.pendingRequests.isNotEmpty()) {
                            Divider(
                                color = Color(0xFFF3F4F6),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )

                            Text(
                                "ЗАЯВКИ НА ВСТУПЛЕНИЕ",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B),
                            )

                            state.pendingRequests.forEach { req ->
                                ParticipantRowDetailed(
                                    name = req.name,
                                    email = req.email,
                                    isPending = true,
                                    onAccept = {
                                        screenModel.handleIntent(
                                            MoreTabIntent.ResolveRequest(
                                                req.id,
                                                true,
                                            ),
                                        )
                                    },
                                    onDecline = {
                                        screenModel.handleIntent(
                                            MoreTabIntent.ResolveRequest(
                                                req.id,
                                                false,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Файлы и документы", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    FilePicker(onFilePicked = { bytes, name ->
                        if (bytes != null && name != null) {
                            screenModel.handleIntent(MoreTabIntent.UploadFile(bytes, name))
                        }
                    }) { onClick ->
                        IconButton(onClick = onClick) {
                            Icon(
                                Icons.Default.FileUpload,
                                contentDescription = "Upload",
                                tint = Color.Gray,
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (state.mediaItems.isEmpty()) {
                            Text("Нет загруженных файлов", fontSize = 14.sp, color = Color.Gray)
                        } else {
                            state.mediaItems.take(3).forEachIndexed { index, item ->
                                FilePreviewRow(
                                    icon = if (item.type == MediaType.DOCUMENT) Icons.Default.Description else Icons.Default.Image,
                                    title = item.title,
                                    subtitle = item.subtitle,
                                    isLast = index == state.mediaItems.take(3).size - 1,
                                    onClick = { downloadTarget = item },
                                )
                            }

                            if (state.mediaItems.size > 3) {
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(
                                    onClick = { parentNavigator.push(FilesScreen(tripId)) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "Посмотреть все (${state.mediaItems.size}) →",
                                        color = Color(0xFF155DFC),
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Чек-лист сборов", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "$completedChecklist/$totalChecklist",
                                fontSize = 14.sp,
                                color = Color.Gray,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        state.checklist.take(4).forEach { item ->
                            val completedList = item.completedBy
                            val isDone =
                                if (item.isGroup) {
                                    completedList.size >= state.list.size
                                } else {
                                    completedList.contains(
                                        state.currentUser?.id.toString(),
                                    )
                                }

                            ChecklistPreviewRow(
                                title = item.title,
                                isCompleted = isDone,
                                onClick = {
                                    screenModel.handleIntent(
                                        MoreTabIntent.ToggleChecklistItem(
                                            item.id,
                                        ),
                                    )
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { parentNavigator.push(ChecklistScreen(tripId)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Открыть полный список →",
                                color = Color(0xFF155DFC),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "УПРАВЛЕНИЕ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        SettingsMenuRow(
                            icon = Icons.Default.People,
                            title = "Участники",
                            badge = activeCount.toString(),
                        ) { parentNavigator.push(ParticipantsScreen(tripId)) }
                        Divider(
                            color = backgroundColor,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        SettingsMenuRow(
                            icon = Icons.Default.InsertDriveFile,
                            title = "Файлы и документы",
                            badge = state.mediaItems.size.toString(),
                        ) { parentNavigator.push(FilesScreen(tripId)) }
                        Divider(
                            color = backgroundColor,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        SettingsMenuRow(
                            icon = Icons.Default.Checklist,
                            title = "Чек-лист сборов",
                            badge = totalChecklist.toString(),
                        ) { parentNavigator.push(ChecklistScreen(tripId)) }
                        Divider(
                            color = backgroundColor,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        SettingsMenuRow(
                            icon = Icons.Default.History,
                            title = "История активности",
                        ) { parentNavigator.push(HistoryScreen(tripId)) }
                    }
                }
            }

            item {
                Text(
                    "НАСТРОЙКИ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        SettingsMenuRow(
                            icon = Icons.Default.NotificationsNone,
                            title = "Уведомления",
                        ) { }
                        Divider(
                            color = backgroundColor,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        SettingsMenuRow(
                            icon = Icons.Default.Settings,
                            title = "Настройки поездки",
                            // badge = "Скоро",
                        ) { }
                    }
                }
            }

            item {
                Text(
                    "ДЕЙСТВИЯ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        SettingsMenuRow(
                            icon = Icons.Default.Share,
                            title = "Поделиться поездкой",
                        ) { screenModel.handleIntent(MoreTabIntent.ShowAddDialog) }
                        Divider(
                            color = backgroundColor,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        SettingsMenuRow(
                            icon = Icons.Default.Archive,
                            title = "Архивировать",
                        ) { screenModel.handleIntent(MoreTabIntent.ArchiveTrip(false)) }
                        Divider(
                            color = backgroundColor,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        SettingsMenuRow(
                            icon = Icons.Default.DeleteForever,
                            title = if (isOwner) "Удалить поездку" else "Покинуть поездку",
                        ) { showDeleteWarning = true }
                    }
                }
            }

            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFECFDF5))
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Все синхронизировано",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF065F46),
                        )
                        Text(
                            "Последнее обновление: сейчас",
                            fontSize = 12.sp,
                            color = Color(0xFF047857),
                        )
                    }
                }
            }
        }

        if (state.isAddDialogVisible) {
            ShareInviteDialog(
                code = state.trip?.joinCode ?: "...",
                onRegenerate = { screenModel.handleIntent(MoreTabIntent.RegenerateCode) },
                onInviteByEmail = {
                    screenModel.handleIntent(MoreTabIntent.HideAddDialog)
                    screenModel.handleIntent(MoreTabIntent.ShowInviteEmailDialog)
                },
                onDismiss = { screenModel.handleIntent(MoreTabIntent.HideAddDialog) },
            )
        }

        if (state.isInviteEmailDialogVisible) {
            InviteByEmailDialog(
                inFlight = state.isInviteInFlight,
                createdInvitationId = state.lastCreatedInvitationId,
                onSend = { email, role ->
                    screenModel.handleIntent(MoreTabIntent.InviteByEmail(email, role))
                },
                onAcknowledgeShared = {
                    screenModel.handleIntent(MoreTabIntent.AcknowledgeInvitationShared)
                },
                onDismiss = { screenModel.handleIntent(MoreTabIntent.HideInviteEmailDialog) },
            )
        }

        if (showDeleteWarning) {
            AlertDialog(
                onDismissRequest = { showDeleteWarning = false },
                title = { Text(if (isOwner) "Удалить поездку?" else "Покинуть поездку?") },
                text = { Text("Это действие нельзя отменить.") },
                confirmButton = {
                    DSButton(
                        text = if (isOwner) "Удалить" else "Покинуть",
                        onClick = {
                            showDeleteWarning = false
                            screenModel.handleIntent(MoreTabIntent.DeleteOrLeaveTrip)
                        },
                        backgroundColor = Color.Red,
                    )
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteWarning = false }) { Text("Отмена") }
                },
            )
        }

        if (downloadTarget != null) {
            AlertDialog(
                onDismissRequest = { downloadTarget = null },
                title = { Text("Скачать файл?") },
                text = { Text("\"${downloadTarget!!.title}\" будет сохранён в папку загрузок.") },
                confirmButton = {
                    DSButton(
                        text = "Скачать",
                        onClick = {
                            val target = downloadTarget!!
                            downloadTarget = null
                            coroutineScope.launch {
                                val url = screenModel.getDownloadUrl(target) ?: return@launch
                                val ext = target.title.substringAfterLast(".", "file")
                                downloadFile(url, target.title.ifBlank { "file.$ext" })
                            }
                        },
                    )
                },
                dismissButton = {
                    TextButton(onClick = { downloadTarget = null }) { Text("Отмена") }
                },
            )
        }
    }
}

@Composable
fun ParticipantRowDetailed(
    name: String,
    email: String,
    role: String? = null,
    isPending: Boolean = false,
    onAccept: (() -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    userId: String? = null,
    avatarUrl: String? = null,
) {
    val isLeft = role == "LEFT"

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isPending || isLeft) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF3F4F6)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.take(1).uppercase(),
                    color = Color.Gray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            GradientAvatar(
                seed = (userId ?: "") + name,
                initials = avatarInitials(name),
                avatarUrl = avatarUrl,
                size = 40.dp,
                fontSize = 16.sp,
                showBorder = false,
            )
        }
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPending || isLeft) Color.Gray else Color.Black,
            )
            Text(text = email, fontSize = 14.sp, color = Color.Gray)
        }

        if (isPending && onAccept != null && onDecline != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onDecline) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Decline",
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onAccept,
                    modifier = Modifier.size(32.dp).background(Color(0xFFECFDF5), CircleShape),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Accept",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .background(
                            if (isLeft) Color(0xFFFFE4E6) else Color(0xFFF3F4F6),
                            RoundedCornerShape(12.dp),
                        ).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                val roleName =
                    when (role) {
                        "OWNER" -> "Владелец"
                        "EDITOR" -> "Редактор"
                        "LEFT" -> "Покинул(а)"
                        else -> "Просмотр"
                    }
                Text(
                    text = roleName,
                    fontSize = 12.sp,
                    color = if (isLeft) Color(0xFFE11D48) else Color.DarkGray,
                )
            }
        }
    }
}

@Composable
fun FilePreviewRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isLast: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEFF6FF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF3B82F6),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
    }
}

@Composable
fun ChecklistPreviewRow(
    title: String,
    isCompleted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            color = if (isCompleted) Color.Gray else Color.Black,
            textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    badge: String? = null,
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
            icon,
            contentDescription = null,
            tint = Color.DarkGray,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            modifier = Modifier.weight(1f),
        )

        if (badge != null) {
            Box(
                modifier =
                    Modifier
                        .background(Color(0xFFF3F4F6), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(badge, fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
    }
}

@Composable
fun ShareInviteDialog(
    code: String,
    onRegenerate: () -> Unit,
    onInviteByEmail: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пригласить друзей") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Поделитесь кодом. Друзья отправят вам заявку на вступление.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier =
                        Modifier
                            .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                            .padding(24.dp),
                ) {
                    Text(
                        code,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF155DFC),
                        letterSpacing = 2.sp,
                    )
                }

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = onRegenerate) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Сгенерировать новый код", color = Color.Red)
                }
                Text(
                    "Старый код сразу перестанет работать.",
                    fontSize = 10.sp,
                    color = Color.Gray,
                )

                Spacer(Modifier.height(12.dp))
                Divider(color = Color(0xFFE5E7EB))
                Spacer(Modifier.height(12.dp))

                TextButton(onClick = onInviteByEmail) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Пригласить по email")
                }
            }
        },
        confirmButton = {
            DSButton(
                text = "Готово",
                onClick = onDismiss,
            )
        },
        containerColor = Color.White,
    )
}
