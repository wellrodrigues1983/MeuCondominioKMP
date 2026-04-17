package br.tec.wrcoder.meucondominio.core.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.koin.core.context.GlobalContext

private const val PREFS_NAME = "meu_condominio_secure"

class AndroidSecureStorage(context: Context) : SecureStorage {
    private val prefs = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

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
}

actual fun createSecureStorage(): SecureStorage {
    val ctx = GlobalContext.get().get<Context>()
    return AndroidSecureStorage(ctx)
}
