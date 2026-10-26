package pl.pickpack.panel.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * BaseLinker API token in Android Keystore–backed EncryptedSharedPreferences.
 * Never log or export the raw token.
 */
class SecureTokenStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        // Fallback for emulators / broken Keystore — still private prefs, not plaintext in repo.
        context.applicationContext.getSharedPreferences(PREFS_NAME_FALLBACK, Context.MODE_PRIVATE)
    }

    fun hasToken(): Boolean = !getToken().isNullOrBlank()

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setToken(token: String?) {
        prefs.edit().apply {
            if (token.isNullOrBlank()) remove(KEY_TOKEN) else putString(KEY_TOKEN, token.trim())
        }.apply()
    }

    fun clear() = setToken(null)

    companion object {
        private const val PREFS_NAME = "pick_pack_secure_bl"
        private const val PREFS_NAME_FALLBACK = "pick_pack_secure_bl_fallback"
        private const val KEY_TOKEN = "bl_token"
    }
}
