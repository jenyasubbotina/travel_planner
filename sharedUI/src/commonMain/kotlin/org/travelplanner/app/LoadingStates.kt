package org.travelplanner.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.travelplanner.app.theme.AppColors
import org.travelplanner.app.theme.AppTypography
import org.travelplanner.app.theme.DSButton
import org.travelplanner.app.theme.DSLoadingOverlay
import org.travelplanner.app.theme.DSPullToRefreshMock
import org.travelplanner.app.theme.DSSkeletonBlock
import org.travelplanner.app.theme.DSSkeletonListItem
import org.travelplanner.app.theme.DSThreeDotsLoader

@Preview(heightDp = 2000, widthDp = 1000)
@Composable
fun LoadingStatesScreen() {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Состояния загрузки\n(Loading States)",
            style = AppTypography.headlineMedium
        )

        DesignSection("Skeleton Screens") {
            DSSkeletonBlock(height = 160.dp, cornerRadius = 0.dp)

            Column(modifier = Modifier.padding(16.dp)) {
                DSSkeletonBlock(height = 16.dp, width = 224.dp)
                Spacer(modifier = Modifier.height(12.dp))
                DSSkeletonBlock(height = 12.dp, width = 150.dp)
                Spacer(modifier = Modifier.height(16.dp))
                DSSkeletonBlock(height = 8.dp, width = 300.dp)
                Spacer(modifier = Modifier.height(8.dp))
                DSSkeletonBlock(height = 8.dp, width = 100.dp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                DSSkeletonListItem()
                DSSkeletonListItem()
                DSSkeletonListItem()
            }
        }

        DesignSection("Спиннеры") {
            Row(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = AppColors.SkeletonColor,
                    strokeWidth = 4.dp
                )

                Spacer(modifier = Modifier.width(32.dp))

                DSThreeDotsLoader()
            }
        }

        DesignSection("Индикаторы прогресса") {
            Column {
                Text("Загрузка...", style = AppTypography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = AppColors.Primary,
                    trackColor = AppColors.SkeletonColor
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column {
                Text("Синхронизация...", style = AppTypography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(AppColors.SkeletonColor, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(4.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, AppColors.Primary, Color.Transparent)
                                )
                            )
                    )
                }
            }
        }

        DesignSection("Оверлей загрузки") {
            DSLoadingOverlay()
        }

        DesignSection("Pull to Refresh") {
            DSPullToRefreshMock()
        }

        DesignSection("Состояния кнопок") {
            DSButton(
                text = "Загрузка...",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                isLoading = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            DSButton(
                text = "Кнопка отключена",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            )

            Spacer(modifier = Modifier.height(12.dp))

            DSButton(
                text = "Успешно",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                isSuccess = true
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}