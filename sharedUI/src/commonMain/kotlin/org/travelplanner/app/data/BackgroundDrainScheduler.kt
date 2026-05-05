package org.travelplanner.app.data

expect class BackgroundDrainScheduler {
    fun schedulePeriodic()

    fun scheduleOneShot()

    fun cancelAll()
}
