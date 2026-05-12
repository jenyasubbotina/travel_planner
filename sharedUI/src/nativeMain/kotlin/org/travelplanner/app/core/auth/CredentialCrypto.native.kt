package org.travelplanner.app.core.auth

internal actual fun encryptSecret(plain: String): String = plain

internal actual fun decryptSecret(encrypted: String): String = encrypted
