package org.travelplanner.app.features.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.Navigator
import coil3.compose.AsyncImage
import org.travelplanner.app.core.UserSession

private val AvatarPalette =
    listOf(
        "#FF6B6B",
        "#4ECDC4",
        "#45B7D1",
        "#96CEB4",
        "#FFEAA7",
        "#DDA0DD",
        "#98D8C8",
        "#F7DC6F",
    )

internal fun avatarGradientFor(seed: String): Brush {
    val hash = seed.hashCode()
    val c1 = AvatarPalette[(hash and 0x7FFFFFFF) % AvatarPalette.size]
    val c2 = AvatarPalette[((hash shr 16) and 0x7FFFFFFF) % AvatarPalette.size]
    return Brush.linearGradient(
        colors = listOf(Color(parseHexColor(c1)), Color(parseHexColor(c2))),
    )
}

internal fun avatarInitials(name: String?): String? =
    name
        ?.split(" ")
        ?.mapNotNull { it.firstOrNull() }
        ?.joinToString("")
        ?.take(2)
        ?.uppercase()
        ?.takeIf { it.isNotBlank() }

private fun parseHexColor(hex: String): Long =
    try {
        ("FF" + hex.removePrefix("#")).toLong(16)
    } catch (e: Exception) {
        0xFF6366F1
    }

@Composable
fun GradientAvatar(
    seed: String,
    initials: String?,
    avatarUrl: String?,
    size: Dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    showBorder: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val borderModifier =
        if (showBorder) {
            Modifier.border(1.5.dp, Color.White, CircleShape)
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(brush = avatarGradientFor(seed), shape = CircleShape)
                .then(borderModifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !avatarUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(size).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }

            initials != null -> {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                )
            }

            else -> {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.6f),
                )
            }
        }
    }
}

@Composable
fun ProfileAvatar(
    userSession: UserSession,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    val currentUser by userSession.currentUser.collectAsState()
    val seed = (currentUser?.id ?: "") + (currentUser?.name ?: "")
    val initials = avatarInitials(currentUser?.name)

    GradientAvatar(
        seed = seed,
        initials = initials,
        avatarUrl = currentUser?.avatarUrl,
        size = 32.dp,
        fontSize = 12.sp,
        showBorder = true,
        modifier = modifier.clickable { navigator.push(ProfileScreen()) },
    )
}
