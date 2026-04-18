package br.tec.wrcoder.meucondominio.core.storage

import platform.Foundation.NSUserDefaults

// TODO(iOS security): substituir por Keychain (SecItemAdd/Copy) antes de release.
// NSUserDefaults é file-backed e suficiente para dev/scaffold, mas não é criptografado.
class IosSecureStorage(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SecureStorage {
    override fun put(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override fun get(key: String): String? = defaults.stringForKey(key)

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }

    override fun clear() {
        val domain = NSUserDefaults.standardUserDefaults.dictionaryRepresentation().keys
        domain.filterIsInstance<String>().forEach { defaults.removeObjectForKey(it) }
    }
}

actual fun createSecureStorage(): SecureStorage = IosSecureStorage()
