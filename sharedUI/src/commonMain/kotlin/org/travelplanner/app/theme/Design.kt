package org.travelplanner.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppColors {
    val Primary = Color(0xFF155DFC)
    val Secondary = Color(0xFF9810FA)
    val Success = Color(0xFF00C950)
    val Error = Color(0xFFFB2C36)
    val Warning = Color(0xFFFF6900)

    val Dark = Color(0xFF101828)
    val Gray = Color(0xFFD1D5DC)
    val Light = Color(0xFFF3F4F6)
    val CardHeaderBg = Color(0xFFF9FAFB)

    val TextPrimary = Color(0xFF0A0A0A)
    val TextSecondary = Color(0xFF6A7282)
    val TextTertiary = Color(0xFF4A5565)
    val TextBlue = Color(0xFF1447E6)

    val Border = Color(0xFFE5E7EB)

    val ChipBgInactive = Color(0xFFF3F4F6)
    val ChipTextInactive = Color(0xFF364153)

    val BgSuccessLight = Color(0xFFDCFCE7)
    val TextSuccess = Color(0xFF008236)

    val BgWarningLight = Color(0xFFFFEDD4)
    val TextWarning = Color(0xFFCA3500)

    val BgBlueLight = Color(0xFFDBEAFE)

    val InfoBg = Color(0xFFEFF6FF)
    val InfoBorder = Color(0xFFBEDBFF)
    val InfoText = Color(0xFF1C398E)
    val InfoIcon = Color(0xFF155DFC)

    val SuccessBg = Color(0xFFF0FDF4)
    val SuccessBorder = Color(0xFFB9F8CF)
    val SuccessText = Color(0xFF0D542B)
    val SuccessIcon = Color(0xFF00A63E)

    val WarningBg = Color(0xFFFFF7ED)
    val WarningBorder = Color(0xFFFFD6A7)
    val WarningText = Color(0xFF7E2A0C)
    val WarningIcon = Color(0xFFF54900)

    val ErrorBg = Color(0xFFFEF2F2)
    val ErrorBorder = Color(0xFFFFC9C9)
    val ErrorText = Color(0xFF82181A)
    val ErrorIcon = Color(0xFFE7000B)

    val ListItemBackground = Color(0xFFF9FAFB)
    val AvatarBlue = Color(0xFF2B7FFF)
    val AvatarPurple = Color(0xFFAD46FF)

    val SkeletonColor = Color(0xFFE5E7EB)
    val ButtonSuccess = Color(0xFF00A63E)
}

val AppTypography =
    Typography(
        headlineLarge =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                color = AppColors.TextPrimary,
            ),
        headlineMedium =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                color = AppColors.TextPrimary,
            ),
        titleLarge =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                color = AppColors.TextPrimary,
            ),
        titleMedium =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                color = AppColors.TextPrimary,
            ),
        bodyLarge =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = AppColors.TextPrimary,
            ),
        bodyMedium =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = AppColors.TextTertiary,
            ),
        labelSmall =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = AppColors.TextSecondary,
            ),
    )
