package org.travelplanner.app.features.tripDetails.balance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberNavigatorScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.travelplanner.app.core.TripUtils.formatDate
import org.travelplanner.app.features.profile.ui.GradientAvatar
import org.travelplanner.app.features.profile.ui.avatarInitials
import org.travelplanner.app.theme.DSNotificationBanner
import kotlin.math.abs

val GreenStart = Color(0xFF00C950)
val GreenEnd = Color(0xFF009966)
val PurpleStart = Color(0xFFAD46FF)
val PinkEnd = Color(0xFFF6339A)
val BlueAction = Color(0xFF155DFC)
val TextBlack = Color(0xFF0A0A0A)
val TextGray = Color(0xFF6A7282)
val PositiveGreen = Color(0xFF00A63E)
val NegativeRed = Color(0xFFE7000B)
val LightBlueBg = Color(0xFFEFF6FF)
val LightBlueBorder = Color(0xFFBEDBFF)
val LightBlueText = Color(0xFF1C398E)

data class BalanceTab(
    private val tripId: String,
) : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Balance)
            return remember { TabOptions(index = 3u, title = "Баланс", icon = icon) }
        }

    @Composable
    override fun Content() {
        val parentNavigator = LocalNavigator.currentOrThrow.parent!!
        val screenModel =
            parentNavigator.rememberNavigatorScreenModel<BalanceScreenModel>(tag = tripId) {
                GlobalContext.get().get<BalanceScreenModel> { parametersOf(tripId) }
            }

        val state by screenModel.state.collectAsState()

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF9FAFB))
                    .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                BalanceHeaderCard(
                    state.currentUserNetBalance,
                    state.involvementCount,
                    state.currency,
                )
            }

            item {
                OptimizationCard(
                    isLoading = state.isOptimizationLoading,
                    onClick = { screenModel.handleIntent(BalanceIntent.OptimizeSettlements) },
                )
            }

            if (state.isOptimizationApplied) {
                item {
                    OptimizationAppliedBanner(message = state.optimizationMessage)
                }
            }

            item {
                ParticipantsCard(state.participants, state.currency)
            }

            if (state.paymentsToMake.isNotEmpty()) {
                item {
                    Text(
                        "К оплате",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextBlack,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(state.paymentsToMake) { payment ->
                    PaymentActionCard(payment, state.currency) {
                        screenModel.handleIntent(BalanceIntent.MarkAsPaid(payment))
                    }
                }
            }

            if (state.history.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Погашено",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextBlack,
                        )
                        Text("${state.history.size}", fontSize = 12.sp, color = TextGray)
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier =
                            Modifier.shadow(
                                1.dp,
                                RoundedCornerShape(16.dp),
                            ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            state.history.forEachIndexed { index, item ->
                                HistoryRow(item, state.currency)
                                if (index < state.history.lastIndex) {
                                    Spacer(Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                TipCard()
            }
        }
    }
}

fun Modifier.shadow(
    elevation: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape,
) = this.then(
    Modifier.graphicsLayer {
        shadowElevation = elevation.toPx()
        this.shape = shape
        clip = true
    },
)

val RedStart = Color(0xFFFF6900)
val RedEnd = Color(0xFFE7000B)
val BlueSettledStart = Color(0xFF155DFC)
val BlueSettledEnd = Color(0xFF2B7FFF)

@Composable
fun BalanceHeaderCard(
    netBalance: Double,
    peopleCount: Int,
    currency: String,
) {
    val isDebt = netBalance < -1.0
    val isCredit = netBalance > 1.0
    val isSettled = !isDebt && !isCredit

    val gradientColors =
        when {
            isCredit -> listOf(GreenStart, GreenEnd)
            isDebt -> listOf(RedStart, RedEnd)
            else -> listOf(BlueSettledStart, BlueSettledEnd)
        }

    val titleText =
        when {
            isCredit -> "Вам должны"
            isDebt -> "Вы должны"
            else -> "Баланс"
        }

    val subtitleText =
        when {
            isCredit -> "$peopleCount человека должны вернуть деньги"
            isDebt -> "Вы должны вернуть деньги $peopleCount людям"
            else -> "Все долги погашены"
        }

    val iconVector =
        when {
            isCredit -> Icons.Default.TrendingUp
            isDebt -> Icons.Default.TrendingDown
            else -> Icons.Default.Check
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(136.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(gradientColors))
                .padding(20.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    text = titleText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(4.dp))

                val amountStr =
                    if (isSettled) {
                        "${currency}0"
                    } else {
                        "${currency}${
                            kotlin.math.abs(netBalance).toInt()
                        }"
                    }

                Text(
                    text = amountStr,
                    color = Color.White,
                    fontSize = 30.sp,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(24.dp).background(Color.White.copy(0.2f), CircleShape))
                    Icon(
                        Icons.Default.Person,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = subtitleText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .background(Color.White.copy(0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconVector, null, tint = Color.White)
        }
    }
}

@Composable
fun OptimizationCard(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(PurpleStart, PinkEnd)))
                .clickable(enabled = !isLoading, onClick = onClick)
                .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .background(Color.White.copy(0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "ОПТИМИЗИРОВАТЬ РАСХОДЫ",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                    Text(
                        "Минимизировать количество переводов",
                        color = Color.White.copy(0.9f),
                        fontSize = 12.sp,
                    )
                }
            }
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Icon(Icons.Default.ArrowForward, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun OptimizationAppliedBanner(message: String?) {
    val subtitle = message ?: "Переводы обновлены"
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDF8FF)),
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE9D5FF), RoundedCornerShape(20.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .background(Color(0xFFF5E8FF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFF7E22CE),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Оптимизация применена",
                    color = Color(0xFF7E22CE),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF9333EA),
                    fontSize = 14.sp,
                )
                Text(
                    text = "Экономия времени!",
                    color = Color(0xFF9333EA),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
fun ParticipantsCard(
    participants: List<ParticipantBalanceItem>,
    currency: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Участники", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextBlack)
            Spacer(Modifier.height(12.dp))

            participants.forEachIndexed { index, person ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradientAvatar(
                            seed = person.userId + person.name,
                            initials = avatarInitials(person.name),
                            avatarUrl = person.avatarUrl,
                            size = 40.dp,
                            fontSize = 16.sp,
                            showBorder = false,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (person.isCurrentUser) "${person.name} (Вы)" else person.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "Потратил ${currency}${person.spent.toInt()}",
                                fontSize = 12.sp,
                                color = TextGray,
                            )
                        }
                    }

                    val bal = person.netBalance.toInt()
                    val sign = if (bal > 0) "+" else ""
                    val color =
                        if (bal > 0) {
                            NegativeRed
                        } else if (bal < 0) {
                            PositiveGreen
                        } else {
                            TextGray
                        }

                    val displayColor =
                        if (bal >= 0) PositiveGreen else Color.Black

                    Text(
                        "$sign${currency}${abs(bal)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = displayColor,
                    )
                }
            }
        }
    }
}

@Composable
fun PaymentActionCard(
    payment: SuggestedPayment,
    currency: String,
    onPay: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().shadow(1.dp, RoundedCornerShape(16.dp)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradientAvatar(
                        seed = payment.fromId.toString() + payment.fromName,
                        initials = avatarInitials(payment.fromName),
                        avatarUrl = null,
                        size = 32.dp,
                        fontSize = 12.sp,
                        showBorder = false,
                    )

                    Icon(
                        Icons.Default.ArrowForward,
                        null,
                        tint = Color(0xFF99A1AF),
                        modifier = Modifier.padding(horizontal = 8.dp).size(16.dp),
                    )

                    GradientAvatar(
                        seed = payment.toId.toString() + payment.toName,
                        initials = avatarInitials(payment.toName),
                        avatarUrl = null,
                        size = 32.dp,
                        fontSize = 12.sp,
                        showBorder = false,
                    )

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            "${payment.fromName} → ${payment.toName}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text("Перевод", fontSize = 12.sp, color = TextGray)
                    }
                }

                Text(
                    "${currency}${payment.amount.toInt()}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onPay,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BlueAction),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("Отметить как оплачено", fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun HistoryRow(
    item: PaymentHistoryItem,
    currency: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .border(1.5.dp, GreenStart, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, null, tint = GreenStart, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(item.title, fontSize = 14.sp, color = TextBlack)
                Text(formatDate(item.date), fontSize = 12.sp, color = TextGray)
            }
        }
        Text("${currency}${item.amount.toInt()}", fontSize = 14.sp, color = TextGray)
    }
}

@Composable
fun TipCard() {
    DSNotificationBanner(
        title = "Совет",
        text = "Используйте оптимизацию, чтобы уменьшить количество переводов между участниками. Это особенно полезно в больших группах!",
        backgroundColor = LightBlueBg,
        borderColor = LightBlueBorder,
        contentColor = LightBlueText,
        iconColor = LightBlueText,
        icon = Icons.Default.Lightbulb,
    )
}
