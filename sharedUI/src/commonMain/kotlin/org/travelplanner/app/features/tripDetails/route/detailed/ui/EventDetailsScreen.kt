package org.travelplanner.app.features.tripDetails.route.detailed.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import coil3.compose.AsyncImage
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.core.FilePicker
import org.travelplanner.app.core.ImagePicker
import org.travelplanner.app.core.rememberClipboardManager
import org.travelplanner.app.core.rememberResolvedImageUrl
import org.travelplanner.app.features.profile.ui.GradientAvatar
import org.travelplanner.app.features.profile.ui.avatarInitials
import org.travelplanner.app.features.tripDetails.more.ShareInviteCodeDialog
import org.travelplanner.app.features.tripDetails.route.ui.EventEditorDialog
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSLoadingOverlay
import org.travelplanner.app.theme.DSTextInput

class EventDetailsScreen(
    val tripId: String,
    val eventId: Long,
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        val screenModel = getScreenModel<EventDetailsScreenModel> { parametersOf(tripId, eventId) }
        val state by screenModel.state.collectAsState()

        var showLinkDialog by remember { mutableStateOf(false) }
        var showShareDialog by remember { mutableStateOf(false) }

        val photos = state.files.filter { it.type == "PHOTO" }
        val documents = state.files.filter { it.type == "DOCUMENT" }

        val clipboardManager = rememberClipboardManager()
        var copiedAddress by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            screenModel.effect.collect { effect ->
                when (effect) {
                    is EventEffect.PopScreen -> {
                        navigator?.pop()
                    }

                    is EventEffect.ShowError -> {
                    }
                }
            }
        }

        if (state.isEditing) {
            EventEditorDialog(
                data = state.editData,
                onIntent = screenModel::handleIntent,
                participants = state.participants,
                currency = state.currency,
            )
        }

        if (showLinkDialog) {
            AddLinkDialog(
                onDismiss = { showLinkDialog = false },
                onAdd = { title, url ->
                    screenModel.handleIntent(EventIntent.AddLink(title, url))
                    showLinkDialog = false
                },
            )
        }

        if (showShareDialog) {
            ShareInviteCodeDialog(
                joinCode =
                    state.event?.let { ev ->
                        "${ev.title}\n${ev.subtitle}\n${ev.time}"
                    },
                onDismiss = { showShareDialog = false },
            )
        }

        if (state.isLoading || state.event == null) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { DSLoadingOverlay() }
            return
        }

        val ev = state.event!!

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(ev.title) },
                    navigationIcon = {
                        IconButton(onClick = { navigator?.pop() }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                "Back",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showShareDialog = true }) {
                            Icon(Icons.Default.Share, "Share")
                        }
                        IconButton(onClick = { screenModel.handleIntent(EventIntent.OpenEditor) }) {
                            Icon(Icons.Default.Edit, null)
                        }
                    },
                )
            },
            containerColor = Color(0xFFF3F4F6),
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    ImagePicker(onImagePicked = { bytes ->
                        if (bytes != null) {
                            screenModel.handleIntent(EventIntent.AddPhoto(bytes))
                        }
                    }) { onClick ->

                        if (photos.isEmpty()) {
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
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (state.isSyncing) {
                                        CircularProgressIndicator(color = Color(0xFFEF4444))
                                    } else {
                                        Icon(
                                            Icons.Default.AddAPhoto,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(48.dp),
                                        )
                                    }
                                    Text(
                                        if (state.isSyncing) "Загрузка..." else "Добавить фото",
                                        color = Color(0xFF9CA3AF),
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                val pagerState = rememberPagerState(pageCount = { photos.size + 1 })

                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxWidth().height(240.dp),
                                ) { page ->
                                    if (page < photos.size) {
                                        val resolved = rememberResolvedImageUrl(photos[page].url)
                                        AsyncImage(
                                            model = resolved,
                                            contentDescription = photos[page].name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(Color(0xFFE5E7EB))
                                                    .clickable(enabled = !state.isSyncing) { onClick() },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (state.isSyncing) {
                                                CircularProgressIndicator(
                                                    modifier =
                                                        Modifier.size(
                                                            24.dp,
                                                        ),
                                                )
                                            } else {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = "Add",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(40.dp),
                                                    )
                                                    Text(
                                                        "Добавить фото",
                                                        color = Color.Gray,
                                                        fontSize = 14.sp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (photos.size > 1 && pagerState.currentPage < photos.size) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(12.dp)
                                                .background(
                                                    Color.Black.copy(alpha = 0.6f),
                                                    RoundedCornerShape(16.dp),
                                                ).padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            "${pagerState.currentPage + 1}/${photos.size}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            ev.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827),
                        )
                        if (!ev.description.isNullOrBlank()) {
                            Text(
                                ev.description!!,
                                fontSize = 15.sp,
                                color = Color(0xFF4B5563),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                val assignedParticipants =
                    state.participants.filter { it.userId in ev.participantIds }

                if (assignedParticipants.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.People,
                                        null,
                                        tint = Color(0xFF6B7280),
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Участники",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    assignedParticipants.forEach { participant ->
                                        Row(
                                            modifier =
                                                Modifier
                                                    .background(
                                                        Color(0xFFEFF6FF),
                                                        RoundedCornerShape(20.dp),
                                                    ).padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            GradientAvatar(
                                                seed = participant.userId + participant.name,
                                                initials = avatarInitials(participant.name),
                                                avatarUrl = participant.avatarUrl,
                                                size = 24.dp,
                                                fontSize = 11.sp,
                                                showBorder = false,
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                participant.name,
                                                fontSize = 13.sp,
                                                color = Color(0xFF1E40AF),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            InfoRow(Icons.Default.LocationOn, "Локация", ev.subtitle)
                            InfoRow(
                                Icons.Default.Schedule,
                                "Время",
                                "${ev.time} • ${ev.duration ?: "1 ч"}",
                            )
                            InfoRow(
                                Icons.Default.AttachMoney,
                                "Бюджет",
                                "${state.currency} ${ev.cost.toInt()}",
                                iconTint = Color(0xFF10B981),
                                iconBg = Color(0xFFD1FAE5),
                            )
                            val statusLabel = when (ev.status) {
                                "BOOKED" -> "Забронировано"
                                "PAID" -> "Оплачено"
                                "PLANNED" -> "Запланировано"
                                "CONFIRMED" -> "Подтверждено"
                                "CANCELLED" -> "Отменено"
                                else -> null
                            }
                            if (statusLabel != null) {
                                InfoRow(
                                    Icons.Default.CheckCircle,
                                    "Статус",
                                    statusLabel,
                                    iconTint = Color(0xFF00A63E),
                                    iconBg = Color(0xFFDCFCE7),
                                )
                            }
                        }
                    }
                }

                val address = ev.address?.takeIf { it.isNotBlank() && it != ev.subtitle }
                val hasCoords = ev.latitude != 0.0 || ev.longitude != 0.0
                if (address != null || hasCoords) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFFEE2E2), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (address != null) "Адрес" else "Координаты",
                                        fontSize = 12.sp,
                                        color = Color(0xFF6B7280),
                                    )
                                    Text(
                                        address ?: "${ev.latitude}, ${ev.longitude}",
                                        fontSize = 14.sp,
                                        color = Color(0xFF111827),
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val payload = if (hasCoords) {
                                            "${ev.latitude},${ev.longitude}"
                                        } else {
                                            address.orEmpty()
                                        }
                                        clipboardManager.copyToClipboard(payload)
                                        copiedAddress = true
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy coordinates",
                                        tint =
                                            if (copiedAddress) {
                                                Color(0xFF10B981)
                                            } else {
                                                Color(
                                                    0xFF6B7280,
                                                )
                                            },
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        if (copiedAddress) {
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(2000)
                                copiedAddress = false
                            }
                        }
                    }
                }

                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DSButton(
                            text = "Маршрут",
                            onClick = { },
                            modifier = Modifier.weight(1f),
                        )
                        DSButton(
                            text = "На карте",
                            onClick = { },
                            modifier = Modifier.weight(1f),
                            isOutline = true,
                        )
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Полезные ссылки",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(12.dp))

                            state.links.forEachIndexed { index, link ->
                                LinkRow(text = link.title, onClick = {
                                })
                                if (index < state.links.size - 1) {
                                    HorizontalDivider(
                                        color = Color(0xFFF3F4F6),
                                        modifier = Modifier.padding(vertical = 12.dp),
                                    )
                                }
                            }

                            if (state.links.isNotEmpty()) Spacer(Modifier.height(12.dp))

                            TextButton(
                                onClick = { showLinkDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("+ Добавить ссылку", color = Color(0xFF155DFC)) }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Заметки и комментарии",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.height(16.dp))

                            state.comments.forEach { comment ->
                                CommentItem(
                                    userId = comment.userId,
                                    name = comment.userName,
                                    text = comment.text,
                                )
                                Spacer(Modifier.height(12.dp))
                            }

                            var localCommentInput by remember { mutableStateOf("") }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                TextField(
                                    value = localCommentInput,
                                    onValueChange = { localCommentInput = it },
                                    placeholder = { Text("Добавить заметку...", fontSize = 14.sp) },
                                    modifier = Modifier.weight(1f).height(50.dp),
                                    colors =
                                        TextFieldDefaults.colors(
                                            unfocusedContainerColor = Color(0xFFF9FAFB),
                                            focusedContainerColor = Color(0xFFF9FAFB),
                                            unfocusedIndicatorColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                        ),
                                    shape = RoundedCornerShape(25.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        screenModel.handleIntent(
                                            EventIntent.AddComment(
                                                localCommentInput,
                                            ),
                                        )
                                        localCommentInput = ""
                                    },
                                    enabled = localCommentInput.isNotBlank() && !state.isSyncing,
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor =
                                                Color(
                                                    0xFF155DFC,
                                                ),
                                        ),
                                    shape = RoundedCornerShape(25.dp),
                                    modifier = Modifier.height(50.dp),
                                ) { Text("Отправить") }
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Билеты и брони", fontSize = 18.sp, fontWeight = FontWeight.Medium)

                        documents.forEach { doc ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                        .clickable { },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    doc.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Download",
                                    tint = Color.Gray,
                                )
                            }
                        }

                        FilePicker(onFilePicked = { bytes, name ->
                            if (bytes != null && name != null) {
                                screenModel.handleIntent(EventIntent.AddDocument(bytes, name))
                            }
                        }) { onClick ->
                            Button(
                                onClick = onClick,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor =
                                            Color(
                                                0xFFF9FAFB,
                                            ),
                                    ),
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                if (state.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.Gray,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.AttachFile,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Добавить файл", color = Color(0xFF4A5565))
                                }
                            }
                        }
                    }
                }

                item {
                    DSButton(
                        text = "Удалить из маршрута",
                        onClick = { screenModel.handleIntent(EventIntent.DeleteEvent) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        backgroundColor = Color(0xFFFEF2F2),
                        contentColor = Color(0xFFDC2626),
                        icon = Icons.Default.DeleteOutline,
                    )
                }
            }
        }
    }
}

@Composable
fun AddLinkDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить ссылку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DSTextInput(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Название",
                    label = "Название",
                    modifier = Modifier.fillMaxWidth(),
                )
                DSTextInput(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://...",
                    label = "URL ссылка",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            DSButton(
                text = "Добавить",
                onClick = { onAdd(title, url) },
                enabled = title.isNotBlank() && url.isNotBlank(),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = Color(0xFF4B5563)) }
        },
    )
}

@Composable
fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String,
    iconTint: Color = Color(0xFF3B82F6),
    iconBg: Color = Color(0xFFDBEAFE),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(iconBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 12.sp, color = Color(0xFF6B7280))
            Text(value, fontSize = 15.sp, color = Color(0xFF111827), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun LinkRow(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Link,
                null,
                tint = Color(0xFF155DFC),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(text, fontSize = 15.sp, color = Color(0xFF111827))
        }
        Icon(
            Icons.Default.ArrowForwardIos,
            null,
            modifier = Modifier.size(14.dp),
            tint = Color(0xFF9CA3AF),
        )
    }
}

@Composable
fun CommentItem(
    userId: String,
    name: String,
    text: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        GradientAvatar(
            seed = userId + name,
            initials = avatarInitials(name),
            avatarUrl = null,
            size = 36.dp,
            fontSize = 14.sp,
            showBorder = false,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827))
            Text(
                text,
                fontSize = 14.sp,
                color = Color(0xFF4B5563),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
