package org.travelplanner.app.features.tripDetails.more.checklist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.getScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.DSEmptyStateCard
import org.travelplanner.app.core.BackendFeatureFlags
import org.travelplanner.app.theme.DSTextInput

class ChecklistScreen(
    val tripId: String,
) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = getScreenModel<ChecklistScreenModel> { parametersOf(tripId) }
        val state by screenModel.state.collectAsState()

        var newItemTitle by remember { mutableStateOf("") }
        var isGroupTask by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Чек-лист сборов", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                )
            },
            bottomBar = {
                Surface(
                    shadowElevation = 8.dp,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isGroupTask,
                                onCheckedChange = { isGroupTask = it },
                                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF155DFC)),
                            )
                            Text("Общая задача (для всех)", fontSize = 14.sp)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            DSTextInput(
                                value = newItemTitle,
                                onValueChange = { newItemTitle = it },
                                placeholder = "Название пункта...",
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            IconButton(
                                onClick = {
                                    screenModel.handleIntent(
                                        ChecklistIntent.AddItem(
                                            newItemTitle,
                                            isGroupTask,
                                        ),
                                    )
                                    newItemTitle = ""
                                },
                                modifier =
                                    Modifier
                                        .size(48.dp)
                                        .background(Color(0xFF155DFC), RoundedCornerShape(12.dp)),
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Add",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF9FAFB))
                        .padding(padding)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.items.isEmpty()) {
                    item {
                        DSEmptyStateCard(
                            title = "Чек-лист пуст",
                            description = "Добавьте пункты, чтобы ничего не забыть в поездку",
                            buttonText = "Понятно",
                            onButtonClick = {},
                            icon = Icons.Default.Check,
                        )
                    }
                }
                items(state.items, key = { it.id }) { item ->
                    val completedList = item.completedBy

                    val isGroup = item.isGroup
                    val isCheckedByMe = completedList.contains(state.currentUserId)
                    val isFullyCompleted =
                        if (isGroup) completedList.size >= state.totalParticipants else isCheckedByMe

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        screenModel.handleIntent(
                                            ChecklistIntent.ToggleItem(
                                                item.id,
                                            ),
                                        )
                                    }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isCheckedByMe) {
                                                Color(0xFF155DFC)
                                            } else {
                                                Color(
                                                    0xFFF3F4F6,
                                                )
                                            },
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isCheckedByMe) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 16.sp,
                                    color = if (isFullyCompleted) Color.Gray else Color.Black,
                                    textDecoration = if (isFullyCompleted) TextDecoration.LineThrough else null,
                                )
                                if (isGroup) {
                                    Text(
                                        text = "Групповая • Выполнили ${completedList.size} из ${state.totalParticipants}",
                                        fontSize = 12.sp,
                                        color =
                                            if (isFullyCompleted) {
                                                Color(0xFF10B981)
                                            } else {
                                                Color(
                                                    0xFFF59E0B,
                                                )
                                            },
                                    )
                                } else {
                                    Text("Личная", fontSize = 12.sp, color = Color.Gray)
                                }
                            }

                            IconButton(onClick = {
                                screenModel.handleIntent(
                                    ChecklistIntent.DeleteItem(
                                        item.id,
                                    ),
                                )
                            }) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = Color.Red,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
