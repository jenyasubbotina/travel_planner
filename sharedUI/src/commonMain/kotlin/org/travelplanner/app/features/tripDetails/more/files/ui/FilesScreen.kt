package org.travelplanner.app.features.tripDetails.more.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.core.TripUtils.isoToEpochMillis
import org.travelplanner.app.core.extractBackendS3KeyOrNull
import org.travelplanner.app.core.toEpochMillis
import org.travelplanner.app.DSEmptyStateCard
import org.travelplanner.app.data.EventRepository
import org.travelplanner.app.data.ExpenseRepository
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.domain.MediaType
import org.travelplanner.app.domain.TripMediaItem
import org.travelplanner.app.features.tripDetails.more.FilePreviewRow

class FilesScreenModel(
    private val tripId: String,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val eventRepository: EventRepository,
    private val json: Json,
) : ScreenModel {
    init {
        screenModelScope.launch { tripRepository.refreshTripFiles(tripId) }
    }

    val filesState =
        combine(
            tripRepository.getTripById(tripId).filterNotNull(),
            expenseRepository.getExpensesFlow(tripId),
            eventRepository.getEventsFlow(tripId),
            tripRepository.getTripLevelAttachmentsFlow(tripId),
        ) { trip, expenses, events, tripAttachments ->
            val files = mutableListOf<TripMediaItem>()

            val tripStartMillis = isoToEpochMillis(trip.startDate)

            trip.imageUrl?.let {
                files.add(
                    TripMediaItem(
                        url = it,
                        title = "Обложка",
                        subtitle = trip.title,
                        type = MediaType.IMAGE,
                        date = tripStartMillis,
                        category = "Поездка",
                        s3Key = it,
                    ),
                )
            }

            expenses.forEach { exp ->
                exp.imageUrl?.let {
                    files.add(
                        TripMediaItem(
                            it,
                            exp.title,
                            "Чек (${exp.category})",
                            MediaType.IMAGE,
                            isoToEpochMillis(exp.date),
                            "Расходы",
                        ),
                    )
                }
            }

            events.forEach { ev ->
                val eventDate = tripStartMillis + (ev.dayIndex * 86400000L)

                ev.files.forEach { file ->
                    files.add(
                        TripMediaItem(
                            url = file.url,
                            title = file.name,
                            subtitle = ev.title,
                            type = if (file.type == "PHOTO") MediaType.IMAGE else MediaType.DOCUMENT,
                            date = eventDate,
                            category = "События",
                            s3Key = file.url,
                        ),
                    )
                }
            }

            tripAttachments.forEach { att ->
                val isImage = att.mimeType.startsWith("image/")
                files.add(
                    TripMediaItem(
                        url = att.s3Key,
                        title = att.fileName,
                        subtitle = "Загруженный файл",
                        type = if (isImage) MediaType.IMAGE else MediaType.DOCUMENT,
                        date = runCatching { att.createdAt.toEpochMillis() }.getOrNull() ?: tripStartMillis,
                        category = "Файлы",
                        s3Key = att.s3Key,
                    ),
                )
            }

            files.sortedByDescending { it.date }.groupBy { it.category }
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    suspend fun getDownloadUrl(item: TripMediaItem): String? {
        val raw = item.s3Key ?: item.url
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val key = extractBackendS3KeyOrNull(raw) ?: return null
        return try {
            tripRepository.getDownloadUrl(key)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

class FilesScreen(
    val tripId: String,
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<FilesScreenModel> { parametersOf(tripId) }
        val groupedFiles by screenModel.filesState.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Файлы и документы", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                )
            },
        ) { padding ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF9FAFB))
                        .padding(padding)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (groupedFiles.isEmpty()) {
                    item {
                        DSEmptyStateCard(
                            title = "Нет файлов",
                            description = "Загрузите брони, билеты и чеки, чтобы всё было под рукой",
                            buttonText = "Вернуться",
                            onButtonClick = { navigator.pop() },
                            icon = Icons.Default.Description,
                        )
                    }
                }

                groupedFiles.forEach { (category, files) ->
                    item {
                        Text(
                            text = category.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                files.forEachIndexed { index, item ->
                                    FilePreviewRow(
                                        icon = if (item.type == MediaType.DOCUMENT) Icons.Default.Description else Icons.Default.Image,
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        isLast = index == files.size - 1,
                                    )
                                    if (index < files.size - 1) {
                                        Divider(
                                            color = Color(0xFFF3F4F6),
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
