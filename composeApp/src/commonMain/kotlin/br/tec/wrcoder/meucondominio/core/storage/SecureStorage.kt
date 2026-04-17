package br.tec.wrcoder.meucondominio.core.storage

interface SecureStorage {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
    fun clear()
}

expect fun createSecureStorage(): SecureStorage
