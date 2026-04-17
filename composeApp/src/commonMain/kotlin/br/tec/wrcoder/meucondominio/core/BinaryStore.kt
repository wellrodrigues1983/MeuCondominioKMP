package br.tec.wrcoder.meucondominio.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory store for user-supplied binary payloads (picked images, uploaded PDFs).
 * Entries are addressed by opaque URLs of the form `memory://{kind}/{id}` so they can
 * be stored in domain models exactly like remote URLs.
 */
class BinaryStore {
    private val _entries = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val entries: StateFlow<Map<String, ByteArray>> = _entries.asStateFlow()

    fun putImage(bytes: ByteArray): String {
        val id = newId()
        val url = "memory://images/$id"
        _entries.value = _entries.value + (url to bytes)
        return url
    }

    fun putFile(fileName: String, bytes: ByteArray): String {
        val id = newId()
        val safeName = fileName.ifBlank { "arquivo" }
        val url = "memory://files/$id/$safeName"
        _entries.value = _entries.value + (url to bytes)
        return url
    }

    fun get(url: String?): ByteArray? = url?.let { _entries.value[it] }

    fun remove(url: String?) {
        if (url == null) return
        _entries.value = _entries.value - url
    }
}
