package com.kcum.gallery.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kcum.gallery.R
import com.kcum.gallery.data.PrefsRepository
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Utiliti keselamatan:
 * - PIN disimpan sebagai SHA-256(salt + pin) - BUKAN teks kosong.
 *   NOTA PRODUKSI: untuk keselamatan maksimum, simpan salt/hash dalam
 *   Android Keystore atau guna androidx.security.crypto. Di sini kita
 *   guna SharedPreferences ringkas kerana ia sudah cukup untuk app-lock
 *   kasual dan mudah dibaca sebagai contoh pembelajaran.
 * - BiometricPrompt untuk cap jari (fingerprint).
 */
object SecurityUtils {

    fun setPin(context: Context, pin: String) {
        val prefs = PrefsRepository.get(context)
        val salt = generateSalt()
        prefs.pinSalt = salt
        prefs.pinHash = hash(pin, salt)
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = PrefsRepository.get(context)
        val salt = prefs.pinSalt ?: return false
        return hash(pin, salt) == prefs.pinHash
    }

    fun hasPin(context: Context): Boolean = PrefsRepository.get(context).hasPin()

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Adakah peranti ada biometrik (cap jari) yang boleh digunakan? */
    fun canUseBiometric(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Papar dialog biometrik. `onSuccess` dipanggil jika pengesahan berjaya.
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onError(errString.toString())
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(activity.getString(R.string.pin_use_pin))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }
}
