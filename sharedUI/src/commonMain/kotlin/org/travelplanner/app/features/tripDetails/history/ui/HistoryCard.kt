package org.travelplanner.app.features.tripDetails.history.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.travelplanner.app.core.TripUtils
import org.travelplanner.app.features.profile.ui.GradientAvatar
import org.travelplanner.app.features.profile.ui.avatarInitials

@Composable
fun HistoryCard(item: HistoryItemUiModel) {
    var isExpanded by remember { mutableStateOf(false) }

    val (entityIcon, actionTint) = remember(item.entityType, item.actionType) {
        iconFor(item.entityType) to tintFor(item.actionType)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    if (item.parsedChanges.isNotEmpty()) {
                        isExpanded = !isExpanded
                    }
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    GradientAvatar(
                        seed = item.userId + item.userName,
                        initials = avatarInitials(item.userName),
                        avatarUrl = item.avatarUrl,
                        size = 40.dp,
                        fontSize = 16.sp,
                        showBorder = false,
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(18.dp)
                                .background(actionTint, CircleShape)
                                .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = entityIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.userName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black,
                    )
                    Text(
                        text = item.actionText,
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = TripUtils.formatDateTime(item.timestamp),
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                    if (item.parsedChanges.isNotEmpty()) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle details",
                            tint = Color.Gray,
                        )
                    }
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color(0xFFF3F4F6))
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF9FAFB), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Детали изменений:",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium,
                    )
                    item.parsedChanges.forEach { changeStr ->
                        Text(
                            text = "• $changeStr",
                            fontSize = 13.sp,
                            color = Color(0xFF155DFC),
                        )
                    }
                }
            }
        }
    }
}

private fun iconFor(entityType: String): ImageVector = when (entityType) {
    "EXPENSE" -> Icons.Default.Payments
    "EVENT" -> Icons.Default.Place
    "TRIP" -> Icons.Default.Flight
    "CHECKLIST_ITEM" -> Icons.Default.CheckCircle
    "PARTICIPANT" -> Icons.Default.Person
    "JOIN_REQUEST" -> Icons.Default.PersonAdd
    "LINK" -> Icons.Default.Link
    "COMMENT" -> Icons.Default.Comment
    "ATTACHMENT" -> Icons.Default.AttachFile
    else -> Icons.Default.Edit
}

private fun tintFor(actionType: String): Color = when (actionType) {
    "CREATE", "JOIN", "COMPLETE", "APPROVE_JOIN" -> Color(0xFF10B981)
    "DELETE", "UNCOMPLETE", "DENY_JOIN", "REJECT_PENDING_UPDATE" -> Color(0xFFEF4444)
    "UPDATE",
    "CHANGE_ROLE",
    "INVITE",
    "REQUEST_JOIN",
    "STORE_PENDING_UPDATE",
    "REGENERATE_JOIN_CODE",
    "REORDER_ITINERARY",
    "ARCHIVE",
    -> Color(0xFF3B82F6)
    else -> Color(0xFF9CA3AF)
}
