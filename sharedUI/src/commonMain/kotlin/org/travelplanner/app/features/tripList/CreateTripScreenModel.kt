package org.travelplanner.app.features.tripList

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.travelplanner.app.core.BaseScreenModel
import org.travelplanner.app.core.CreateTripRequest
import org.travelplanner.app.core.UserDto
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import org.travelplanner.app.domain.Trip
import kotlin.coroutines.cancellation.CancellationException

class CreateTripScreenModel(
    private val repository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val userSession: UserSession,
) : BaseScreenModel<CreateTripState, CreateTripIntent, CreateTripEffect>(CreateTripState()) {
    override fun handleIntent(intent: CreateTripIntent) {
        when (intent) {
            is CreateTripIntent.TitleChanged -> {
                updateState { copy(title = intent.value) }
            }

            is CreateTripIntent.DestinationChanged -> {
                updateState { copy(destination = intent.value) }
            }

            is CreateTripIntent.BudgetChanged -> {
                updateState { copy(budget = intent.value) }
            }

            is CreateTripIntent.DescriptionChanged -> {
                updateState { copy(description = intent.value) }
            }

            is CreateTripIntent.CurrencyChanged -> {
                updateState { copy(currency = intent.value) }
            }

            is CreateTripIntent.DatesChanged -> {
                updateState {
                    copy(
                        startDate = intent.start,
                        endDate = intent.end,
                    )
                }
            }

            is CreateTripIntent.PhotoSelected -> {
                updateState { copy(photoBytes = intent.bytes) }
            }

            is CreateTripIntent.SaveClicked -> {
                save()
            }

            is CreateTripIntent.DismissMessage -> {}
        }
    }

    private fun save() {
        val s = currentState
        val user = userSession.currentUser.value ?: return

        if (s.title.isNotBlank() && s.startDate != null && s.endDate != null) {
            screenModelScope.launch {
                try {
                    var uploadedPhotoUrl: String? = null
                    if (s.photoBytes != null) {
                        uploadedPhotoUrl = repository.uploadPhoto(s.photoBytes)
                    }

                    val request =
                        CreateTripRequest(
                            title = s.title,
                            destination = s.destination,
                            startDate = s.startDate,
                            endDate = s.endDate,
                            totalBudget = s.budget.toDoubleOrNull() ?: 0.0,
                            description = s.description.ifBlank { null },
                            ownerUserId = user.id.toString(),
                            ownerName = user.name,
                            ownerEmail = user.email,
                            currency = s.currency,
                            imageUrl = uploadedPhotoUrl,
                        )

                    val response = repository.createTrip(request)

                    withContext(Dispatchers.IO) {
                        repository.saveServerTrip(
                            Trip(
                                id = response.id,
                                title = response.title,
                                destination = response.destination,
                                startDate = response.startDate,
                                endDate = response.endDate,
                                currency = s.currency,
                                totalBudget = response.totalBudget,
                                description = response.description,
                                ownerUserId = response.ownerUserId,
                                joinCode = response.joinCode,
                                imageUrl = response.imageUrl,
                            ),
                        )
                        participantRepository.insertOrUpdateParticipant(
                            tripId = response.id,
                            userDto =
                                UserDto(
                                    id = user.id.toString(),
                                    name = user.name,
                                    email = user.email,
                                ),
                        )
                    }

                    sendEffect(CreateTripEffect.NavigateBack)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    sendEffect(CreateTripEffect.ShowMessage("Ошибка при создании: проверьте данные"))
                }
            }
        }
    }
}
