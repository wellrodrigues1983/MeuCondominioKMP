package br.tec.wrcoder.meucondominio.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import org.koin.core.context.GlobalContext

private const val PREFS_NAME = "meu_condominio_secure"
private const val TAG = "SecureStorage"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"

class AndroidSecureStorage(context: Context) : SecureStorage {
    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences =
        try {
            buildEncryptedPrefs(context)
        } catch (t: Throwable) {
            Log.w(TAG, "EncryptedSharedPreferences corrupted, resetting", t)
            resetEncryptedPrefs(context)
            buildEncryptedPrefs(context)
        }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun resetEncryptedPrefs(context: Context) {
        runCatching { context.deleteSharedPreferences(PREFS_NAME) }
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE)
                .apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }
}

actual fun createSecureStorage(): SecureStorage {
    val ctx = GlobalContext.get().get<Context>()
    return AndroidSecureStorage(ctx)
}
