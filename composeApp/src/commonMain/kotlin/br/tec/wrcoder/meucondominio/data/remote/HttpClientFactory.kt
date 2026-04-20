package br.tec.wrcoder.meucondominio.data.remote

import br.tec.wrcoder.meucondominio.core.BuildConfig
import br.tec.wrcoder.meucondominio.core.logging.AppLogger
import br.tec.wrcoder.meucondominio.core.storage.AuthTokens
import br.tec.wrcoder.meucondominio.core.storage.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

expect fun httpEngineClient(config: HttpClientConfig<*>.() -> Unit): HttpClient

val ApiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
private data class RefreshRequest(val refreshToken: String)

@Serializable
private data class RefreshResponse(val accessToken: String, val refreshToken: String)

private val RetriedAfter401 = AttributeKey<Boolean>("RetriedAfter401")
private val refreshMutex = Mutex()

private fun isAuthEndpoint(url: String): Boolean =
    url.contains("/auth/login") ||
        url.contains("/auth/refresh") ||
        url.contains("/auth/register-condominium") ||
        url.contains("/auth/join-condominium")

fun createHttpClient(tokenStore: TokenStore): HttpClient {
    val client = httpEngineClient {
        expectSuccess = true
        install(ContentNegotiation) {
            json(ApiJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
        install(Logging) {
            val ktorLog = AppLogger.withTag("Ktor")
            logger = object : Logger {
                override fun log(message: String) {
                    ktorLog.d { message }
                }
            }
            level = LogLevel.HEADERS
        }
        install(WebSockets) {
            pingIntervalMillis = 20_000
        }
        install(HttpSend)
        install(Auth) {
            bearer {
                loadTokens {
                    tokenStore.read()?.let { BearerTokens(it.accessToken, it.refreshToken) }
                }
                refreshTokens {
                    val current = tokenStore.read() ?: return@refreshTokens null
                    try {
                        val refreshed: RefreshResponse = client
                            .post("${BuildConfig.API_BASE_URL.trimEnd('/')}/auth/refresh") {
                                markAsRefreshTokenRequest()
                                contentType(ContentType.Application.Json)
                                setBody(RefreshRequest(current.refreshToken))
                            }.body()
                        val fresh = AuthTokens(refreshed.accessToken, refreshed.refreshToken)
                        tokenStore.updateTokens(fresh)
                        BearerTokens(fresh.accessToken, fresh.refreshToken)
                    } catch (_: Throwable) {
                        tokenStore.clear()
                        null
                    }
                }
                sendWithoutRequest { req ->
                    !isAuthEndpoint(req.url.buildString())
                }
            }
        }
        defaultRequest {
            url(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
            contentType(ContentType.Application.Json.withCharset(Charsets.UTF_8))
        }
    }

    client.plugin(HttpSend).intercept { request ->
        val call = execute(request)
        val url = request.url.buildString()
        val alreadyRetried = request.attributes.getOrNull(RetriedAfter401) == true
        if (call.response.status != HttpStatusCode.Unauthorized || alreadyRetried || isAuthEndpoint(url)) {
            return@intercept call
        }
        val fresh = refreshMutex.withLock { forceRefresh(client, tokenStore) }
            ?: return@intercept call
        request.attributes.put(RetriedAfter401, true)
        request.headers[HttpHeaders.Authorization] = "Bearer ${fresh.accessToken}"
        execute(request)
    }

    return client
}

private suspend fun forceRefresh(client: HttpClient, tokenStore: TokenStore): AuthTokens? {
    val current = tokenStore.read() ?: return null
    if (current.refreshToken.isBlank()) return null
    return try {
        val refreshed: RefreshResponse = client
            .post("${BuildConfig.API_BASE_URL.trimEnd('/')}/auth/refresh") {
                header(HttpHeaders.Authorization, "")
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(current.refreshToken))
            }.body()
        val fresh = AuthTokens(refreshed.accessToken, refreshed.refreshToken)
        tokenStore.updateTokens(fresh)
        client.authProvider<BearerAuthProvider>()?.clearToken()
        fresh
    } catch (t: Throwable) {
        AppLogger.withTag("Auth").w(t) { "forceRefresh failed" }
        tokenStore.clear()
        null
    }
}
