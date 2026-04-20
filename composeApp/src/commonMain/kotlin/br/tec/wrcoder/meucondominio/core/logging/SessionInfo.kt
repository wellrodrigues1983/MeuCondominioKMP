package br.tec.wrcoder.meucondominio.core.logging

import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.core.storage.TokenStore

class SessionInfo(private val tokens: TokenStore) {
    val sessionId: String = newId()
    val currentUserId: String? get() = tokens.currentUserId.value
}
