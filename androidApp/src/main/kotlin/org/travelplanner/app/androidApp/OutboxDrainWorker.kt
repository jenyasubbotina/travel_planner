package org.travelplanner.app.androidApp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.travelplanner.app.data.OutboxDrainer

class OutboxDrainWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val drainer: OutboxDrainer by inject()

    override suspend fun doWork(): Result =
        try {
            drainer.drainAllEligible()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
}
