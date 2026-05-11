package org.travelplanner.app.features.tripList

import org.travelplanner.app.core.UiEffect
import org.travelplanner.app.core.UiIntent
import org.travelplanner.app.core.UiState

data class CreateTripState(
    val title: String = "",
    val destination: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val currency: String = "JPY",
    val budget: String = "",
    val description: String = "",
    val photoBytes: ByteArray? = null,
    val showErrors: Boolean = false,
    val titleError: String? = null,
    val datesError: String? = null,
    val currencyError: String? = null,
    val budgetError: String? = null,
) : UiState

sealed interface CreateTripIntent : UiIntent {
    data class TitleChanged(
        val value: String,
    ) : CreateTripIntent

    data class DestinationChanged(
        val value: String,
    ) : CreateTripIntent

    data class BudgetChanged(
        val value: String,
    ) : CreateTripIntent

    data class DescriptionChanged(
        val value: String,
    ) : CreateTripIntent

    data class CurrencyChanged(
        val value: String,
    ) : CreateTripIntent

    data class DatesChanged(
        val start: Long,
        val end: Long,
    ) : CreateTripIntent

    data class PhotoSelected(
        val bytes: ByteArray?,
    ) : CreateTripIntent

    data object SaveClicked : CreateTripIntent
}

sealed interface CreateTripEffect : UiEffect {
    data object NavigateBack : CreateTripEffect

    data class ShowMessage(
        val message: String,
    ) : CreateTripEffect
}
