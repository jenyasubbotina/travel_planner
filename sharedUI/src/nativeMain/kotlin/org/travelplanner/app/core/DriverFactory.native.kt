package org.travelplanner.app.core

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import org.travelplanner.app.db.MyDatabase

actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(
            schema = MyDatabase.Schema,
            name = "trips.db",
        )
}
