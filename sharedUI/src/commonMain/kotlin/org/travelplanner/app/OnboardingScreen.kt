package org.travelplanner.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.travelplanner.app.auth.AuthForm
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.theme.DSButton

private val Blue = Color(0xFF155DFC)
private val GrayText = Color(0xFF4A5565)
private val GrayDot = Color(0xFFD1D5DC)

private data class OnboardingPage(
    val emoji: String,
    val gradientColors: List<Color>,
    val title: String,
    val description: String,
)

private val pages =
    listOf(
        OnboardingPage(
            emoji = "🗺️",
            gradientColors = listOf(Color(0xFF2B7FFF), Color(0xFF155DFC)),
            title = "Планируйте маршруты",
            description = "Создавайте детальные маршруты по дням с точками на карте. Добавляйте заметки, время и бронирования.",
        ),
        OnboardingPage(
            emoji = "👥",
            gradientColors = listOf(Color(0xFFAD46FF), Color(0xFF9810FA)),
            title = "Путешествуйте вместе",
            description = "Приглашайте друзей, делитесь планами и синхронизируйте изменения в реальном времени.",
        ),
        OnboardingPage(
            emoji = "💰",
            gradientColors = listOf(Color(0xFF00C950), Color(0xFF00A63E)),
            title = "Делите расходы",
            description = "Отслеживайте траты, делите счета поровну или по долям. Прикрепляйте чеки и фото.",
        ),
        OnboardingPage(
            emoji = "📊",
            gradientColors = listOf(Color(0xFFFF6900), Color(0xFFF54900)),
            title = "Контролируйте бюджет",
            description = "Следите за бюджетом в реальном времени. Получайте уведомления о расходах и балансе.",
        ),
    )

private const val TOTAL_PAGES = 5

@Composable
fun OnboardingFlow(userSession: UserSession) {
    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES })
    val scope = rememberCoroutineScope()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.White),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
        ) { page ->
            if (page < pages.size) {
                FeaturePage(pages[page])
            } else {
                RegistrationPage(userSession)
            }
        }

        if (pagerState.currentPage < pages.size) {
            TextButton(
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(TOTAL_PAGES - 1) }
                },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp),
            ) {
                Text("Пропустить", fontSize = 14.sp, color = GrayText)
            }
        }

        if (pagerState.currentPage < pages.size) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(pages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 32.dp else 8.dp,
                            animationSpec = tween(300),
                        )
                        val color by animateColorAsState(
                            targetValue = if (isSelected) Blue else GrayDot,
                            animationSpec = tween(300),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .height(8.dp)
                                    .width(width)
                                    .clip(CircleShape)
                                    .background(color),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                val currentPage = pagerState.currentPage
                val isLastFeature = currentPage == pages.size - 1

                if (currentPage == 0) {
                    DSButton(
                        text = "Далее",
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DSButton(
                            text = "Назад",
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                            },
                            isOutline = true,
                        )

                        DSButton(
                            text = if (isLastFeature) "Начать" else "Далее",
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturePage(page: OnboardingPage) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            modifier =
                Modifier
                    .size(128.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.15f),
                        spotColor = Color.Black.copy(alpha = 0.1f),
                    ).background(
                        brush = Brush.linearGradient(page.gradientColors),
                        shape = CircleShape,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(page.emoji, fontSize = 60.sp)
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = page.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF0A0A0A),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = page.description,
            fontSize = 16.sp,
            color = GrayText,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.weight(2f))
    }
}

@Composable
private fun RegistrationPage(userSession: UserSession) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.White),
    ) {
        AuthForm(userSession = userSession)
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Color(0xFF155DFC),
                                    Color(0xFF9810FA),
                                    Color(0xFFF6339A),
                                ),
                        ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(24.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text("✈️", fontSize = 40.sp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Travel Planner",
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )

            Spacer(Modifier.height(0.dp))

            Text(
                text = "Планируйте путешествия.",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Делите расходы легко.",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        val infiniteTransition = rememberInfiniteTransition()
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) { index ->
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec =
                        InfiniteRepeatableSpec(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset =
                                androidx.compose.animation.core
                                    .StartOffset(index * 200),
                        ),
                )
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = alpha)),
                )
            }
        }
    }
}
