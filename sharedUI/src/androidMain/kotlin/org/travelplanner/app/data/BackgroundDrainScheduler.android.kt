package org.travelplanner.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit


actual class BackgroundDrainScheduler(private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    @Suppress("UNCHECKED_CAST")
    private val workerClass: Class<out ListenableWorker> by lazy {
        Class.forName(WORKER_CLASS_NAME) as Class<out ListenableWorker>
    }

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

    actual fun schedulePeriodic() {
        val request = PeriodicWorkRequest.Builder(
            workerClass,
            PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    actual fun scheduleOneShot() {
        val request = OneTimeWorkRequest.Builder(workerClass)
            .setConstraints(constraints)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    actual fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    companion object {
        private const val WORKER_CLASS_NAME = "org.travelplanner.app.androidApp.OutboxDrainWorker"
        private const val UNIQUE_PERIODIC = "outbox-drain-periodic"
        private const val UNIQUE_ONESHOT = "outbox-drain-oneshot"
        private const val TAG = "outbox-drain"

        private const val PERIODIC_INTERVAL_MINUTES = 15L
    }
}
