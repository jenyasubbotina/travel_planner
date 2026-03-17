package org.travelplanner.app.core

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.travelplanner.app.db.MyDatabase

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = MyDatabase.Schema,
            context = context,
            name = "trips.db"
        )
    }
}