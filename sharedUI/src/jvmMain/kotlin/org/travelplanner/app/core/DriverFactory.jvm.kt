package org.travelplanner.app.core

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.travelplanner.app.db.MyDatabase
import java.io.File

actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        val databasePath = File(System.getProperty("user.home"), "TripPlanner.db")
        val driver = JdbcSqliteDriver(url = "jdbc:sqlite:${databasePath.absolutePath}")

        if (!databasePath.exists()) {
            MyDatabase.Schema.create(driver)
        }
        return driver
    }
}
