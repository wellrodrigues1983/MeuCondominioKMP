package br.tec.wrcoder.meucondominio.core.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val ACCESS_TOKEN_KEY = "auth_access_token"
private const val REFRESH_TOKEN_KEY = "auth_refresh_token"
private const val CURRENT_USER_ID_KEY = "auth_current_user_id"

data class AuthTokens(val accessToken: String, val refreshToken: String)

class TokenStore(private val secure: SecureStorage) {

    private val _tokens = MutableStateFlow(readTokens())
    val tokens: StateFlow<AuthTokens?> = _tokens.asStateFlow()

    private val _currentUserId = MutableStateFlow(secure.get(CURRENT_USER_ID_KEY))
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    fun save(tokens: AuthTokens, userId: String) {
        secure.put(ACCESS_TOKEN_KEY, tokens.accessToken)
        secure.put(REFRESH_TOKEN_KEY, tokens.refreshToken)
        secure.put(CURRENT_USER_ID_KEY, userId)
        _tokens.value = tokens
        _currentUserId.value = userId
    }

    fun updateTokens(tokens: AuthTokens) {
        secure.put(ACCESS_TOKEN_KEY, tokens.accessToken)
        secure.put(REFRESH_TOKEN_KEY, tokens.refreshToken)
        _tokens.value = tokens
    }

    fun read(): AuthTokens? = _tokens.value

    fun clear() {
        secure.remove(ACCESS_TOKEN_KEY)
        secure.remove(REFRESH_TOKEN_KEY)
        secure.remove(CURRENT_USER_ID_KEY)
        _tokens.value = null
        _currentUserId.value = null
    }

    private fun readTokens(): AuthTokens? {
        val access = secure.get(ACCESS_TOKEN_KEY) ?: return null
        val refresh = secure.get(REFRESH_TOKEN_KEY) ?: return null
        return AuthTokens(access, refresh)
    }
}
