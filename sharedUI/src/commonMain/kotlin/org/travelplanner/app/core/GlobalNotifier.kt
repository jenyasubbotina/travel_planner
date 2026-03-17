package org.travelplanner.app.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class GlobalNotifier {
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors = _errors.asSharedFlow()

    private val _successes = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val successes = _successes.asSharedFlow()

    fun notifyError(message: String = "Ошибка сети: проверьте подключение и попробуйте снова") {
        _errors.tryEmit(message)
    }

    fun notifySuccess(message: String) {
        _successes.tryEmit(message)
    }
}
