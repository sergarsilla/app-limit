package dev.applimit.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** The optional PIN is stored only as a salted PBKDF2 hash. */
object Pin {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun isValid(pin: String) = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all(Char::isDigit)

    /** Returns "salt:hash", both Base64. */
    fun hash(pin: String): String {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        return encode(salt) + ":" + encode(derive(pin, salt))
    }

    fun matches(pin: String, stored: String): Boolean {
        val (salt, hash) = stored.split(':').takeIf { it.size == 2 } ?: return false
        return MessageDigest.isEqual(derive(pin, decode(salt)), decode(hash))
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS))
            .encoded

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String) = Base64.getDecoder().decode(text)
}
