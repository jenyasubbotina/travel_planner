package org.travelplanner.app

import kotlinx.coroutines.CoroutineDispatcher

/** Фоновые задачи БД/сети. На wasmJs совпадает с [kotlinx.coroutines.Dispatchers.Default]. */
internal expect val AppBackground: CoroutineDispatcher
