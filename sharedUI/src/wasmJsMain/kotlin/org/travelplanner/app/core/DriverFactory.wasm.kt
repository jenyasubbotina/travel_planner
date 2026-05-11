package org.travelplanner.app.core

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.createDefaultWebWorkerDriver
import org.travelplanner.app.db.MyDatabase

actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = createDefaultWebWorkerDriver()
        MyDatabase.Schema.create(driver)
        return driver
    }
}
