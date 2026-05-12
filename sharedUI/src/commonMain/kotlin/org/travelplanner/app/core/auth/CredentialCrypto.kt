package org.travelplanner.app.core.auth

internal expect fun encryptSecret(plain: String): String

internal expect fun decryptSecret(encrypted: String): String
