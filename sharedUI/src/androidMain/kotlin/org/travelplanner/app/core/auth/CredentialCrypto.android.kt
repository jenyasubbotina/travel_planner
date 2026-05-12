package org.travelplanner.app.core.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEY_ALIAS = "travelplanner_credential_key"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_LENGTH = 12
private const val GCM_TAG_BITS = 128

private fun secretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
        KeyGenParameterSpec
            .Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build(),
    )
    return generator.generateKey()
}

internal actual fun encryptSecret(plain: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
    val iv = cipher.iv
    val ciphertext = cipher.doFinal(plain.encodeToByteArray())
    return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
}

internal actual fun decryptSecret(encrypted: String): String {
    val combined = Base64.decode(encrypted, Base64.NO_WRAP)
    val iv = combined.copyOfRange(0, IV_LENGTH)
    val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(ciphertext).decodeToString()
}
