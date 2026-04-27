package org.travelplanner.app.features.tripList

import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.travelplanner.app.core.BaseScreenModel
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.V2CreateTripRequest
import org.travelplanner.app.core.toIsoDate
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
                val budgetForServer = s.budget.takeIf { it.isNotBlank() }
                    ?.toDoubleOrNull()
                    ?.toString()
                val request =
                    V2CreateTripRequest(
                        title = s.title,
                        description = s.description.ifBlank { null },
                        startDate = s.startDate?.toIsoDate(),
                        endDate = s.endDate?.toIsoDate(),
                        baseCurrency = s.currency,
                        totalBudget = budgetForServer,
                        destination = s.destination.ifBlank { null },
                    )

                val response = try {
                    repository.createTrip(request)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    sendEffect(CreateTripEffect.ShowMessage("Ошибка при создании: проверьте данные"))
                    return@launch
                }

                // Save the trip locally FIRST so subsequent PATCH reads the correct version.
                withContext(Dispatchers.IO) {
                    repository.saveServerTrip(
                        Trip(
                            id = response.id,
                            title = response.title,
                            destination = s.destination,
                            startDate = response.startDate,
                            endDate = response.endDate,
                            currency = s.currency,
                            totalBudget = (s.budget.toDoubleOrNull() ?: 0.0).toString(),
                            description = response.description,
                            ownerUserId = response.createdBy,
                            imageUrl = null,
                            version = response.version,
                            baseCurrency = response.baseCurrency,
                            createdBy = response.createdBy,
                            createdAt = response.createdAt,
                            updatedAt = response.updatedAt,
                        ),
                    )
                    participantRepository.syncParticipants(response.id)
                }

                if (s.photoBytes != null) {
                    try {
                        val s3Key = repository.uploadPhoto(response.id, s.photoBytes)
                        repository.setTripImageUrl(response.id, s3Key)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()
                        sendEffect(CreateTripEffect.ShowMessage("Поездка создана, но не удалось загрузить обложку"))
                    }
                }

                sendEffect(CreateTripEffect.NavigateBack)
            }
        }
    }
}
