package org.travelplanner.app.features.tripDetails.summary

import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.travelplanner.app.core.ReactiveScreenModel
import org.travelplanner.app.data.EventRepository
import org.travelplanner.app.data.ExpenseRepository
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository

class TripSummaryScreenModel(
    private val tripId: Long,
    private val tripRepository: TripRepository,
    private val eventsRepository: EventRepository,
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository,
) : ReactiveScreenModel<TripSummaryState, TripSummaryIntent, TripSummaryEffect>() {
    override val state: StateFlow<TripSummaryState> =
        combine(
            tripRepository.getTripById(tripId),
            participantRepository.getParticipantsFlow(tripId),
            expenseRepository.getExpensesFlow(tripId),
            eventsRepository.getEventsFlow(tripId),
        ) { trip, participants, expenses, events ->
            TripSummaryState(
                trip = trip,
                participants = participants,
                expenses = expenses,
                events = events,
            )
        }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), TripSummaryState())

    override fun handleIntent(intent: TripSummaryIntent) {
        when (intent) {
            is TripSummaryIntent.UpdateBudget -> {
                screenModelScope.launch {
                    tripRepository.changeTripBudget(tripId, intent.newBudget)
                }
            }
        }
    }
}

fun getCategoryUiData(category: String): Triple<String, String, Color> =
    when (category.uppercase()) {
        "HOUSING" -> {
            Triple("🏠", "Жильё", Color(0xFF2B7FFF))
        }

        "FOOD" -> {
            Triple("🍱", "Питание", Color(0xFFFF6900))
        }

        "TRANSPORT" -> {
            Triple("🚇", "Транспорт", Color(0xFFAD46FF))
        }

        "ENTERTAINMENT" -> {
            Triple("🎭", "Развлечения", Color(0xFFF6339A))
        }

        "SHOPPING" -> {
            Triple("🛍️", "Покупки", Color(0xFF00C950))
        }

        "PAYMENT" -> {
            Triple("💸", "Переводы", Color(0xFF6A7282))
        }

        else -> {
            Triple(
                "🛒",
                category.lowercase().replaceFirstChar { it.uppercase() },
                Color(0xFF6A7282),
            )
        }
    }
