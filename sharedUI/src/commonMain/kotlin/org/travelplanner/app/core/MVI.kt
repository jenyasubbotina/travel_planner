package org.travelplanner.app.core

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface UiState

interface UiIntent

interface UiEffect

abstract class BaseScreenModel<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
) : ScreenModel {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<E>()
    val effect: SharedFlow<E> = _effect.asSharedFlow()

    abstract fun handleIntent(intent: I)

    protected fun updateState(reducer: S.() -> S) {
        _state.update { it.reducer() }
    }

    protected fun sendEffect(effect: E) {
        screenModelScope.launch {
            _effect.emit(effect)
        }
    }

    protected val currentState: S
        get() = _state.value
}

abstract class ReactiveScreenModel<S : UiState, I : UiIntent, E : UiEffect> : ScreenModel {
    abstract val state: StateFlow<S>

    private val _effect = MutableSharedFlow<E>()
    val effect: SharedFlow<E> = _effect.asSharedFlow()

    abstract fun handleIntent(intent: I)

    protected fun sendEffect(effect: E) {
        screenModelScope.launch {
            _effect.emit(effect)
        }
    }
}
