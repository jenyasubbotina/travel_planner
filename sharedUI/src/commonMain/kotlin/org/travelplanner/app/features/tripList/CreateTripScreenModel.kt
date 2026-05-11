package org.travelplanner.app.features.tripList

import cafe.adriel.voyager.core.model.screenModelScope
import org.travelplanner.app.AppBackground
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.travelplanner.app.core.BaseScreenModel
import org.travelplanner.app.core.UserSession
import org.travelplanner.app.core.V2CreateTripRequest
import org.travelplanner.app.core.Validation
import org.travelplanner.app.core.toIsoDate
import org.travelplanner.app.data.ParticipantRepository
import org.travelplanner.app.data.TripRepository
import kotlin.coroutines.cancellation.CancellationException

class CreateTripScreenModel(
    private val repository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val userSession: UserSession,
) : BaseScreenModel<CreateTripState, CreateTripIntent, CreateTripEffect>(CreateTripState()) {
    override fun handleIntent(intent: CreateTripIntent) {
        when (intent) {
            is CreateTripIntent.TitleChanged -> {
                updateState {
                    copy(
                        title = intent.value,
                        titleError = if (showErrors) computeTitleError(intent.value) else titleError,
                    )
                }
            }

            is CreateTripIntent.DestinationChanged -> {
                updateState { copy(destination = intent.value) }
            }

            is CreateTripIntent.BudgetChanged -> {
                updateState {
                    copy(
                        budget = intent.value,
                        budgetError = if (showErrors) computeBudgetError(intent.value) else budgetError,
                    )
                }
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
                        datesError =
                            if (showErrors) {
                                computeDatesError(
                                    intent.start,
                                    intent.end,
                                )
                            } else {
                                datesError
                            },
                    )
                }
            }

            is CreateTripIntent.PhotoSelected -> {
                updateState { copy(photoBytes = intent.bytes) }
            }

            is CreateTripIntent.SaveClicked -> {
                save()
            }
        }
    }

    private fun computeTitleError(value: String): String? =
        when {
            value.isBlank() -> "Введите название поездки"
            value.length > Validation.TITLE_MAX -> "Слишком длинное название (макс. ${Validation.TITLE_MAX})"
            else -> null
        }

    private fun computeDatesError(
        start: Long?,
        end: Long?,
    ): String? =
        when {
            start == null || end == null -> "Укажите даты поездки"
            end < start -> "Дата окончания раньше начала"
            else -> null
        }

    private fun computeBudgetError(value: String): String? =
        when {
            value.isBlank() -> null
            !Validation.isNonNegativeAmount(value) -> "Бюджет должен быть числом ≥ 0"
            else -> null
        }

    private fun save() {
        val s = currentState
        val user = userSession.currentUser.value ?: return

        val titleError = computeTitleError(s.title)
        val datesError = computeDatesError(s.startDate, s.endDate)
        val budgetError = computeBudgetError(s.budget)

        val hasErrors =
            titleError != null || datesError != null || budgetError != null
        if (hasErrors) {
            updateState {
                copy(
                    showErrors = true,
                    titleError = titleError,
                    datesError = datesError,
                    budgetError = budgetError,
                )
            }
            sendEffect(CreateTripEffect.ShowMessage("Заполните обязательные поля"))
            return
        }

        screenModelScope.launch {
            val budgetForServer =
                s.budget
                    .takeIf { it.isNotBlank() }
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

                val newTripId =
                    withContext(AppBackground) {
                        repository.createTripLocal(request)
                    }

            if (s.photoBytes != null) {
                try {
                    val s3Key = repository.uploadPhoto(newTripId, s.photoBytes)
                    repository.setTripImageUrl(newTripId, s3Key)
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
