package br.tec.wrcoder.meucondominio.data.remote

import br.tec.wrcoder.meucondominio.core.BuildConfig
import br.tec.wrcoder.meucondominio.core.storage.AuthTokens
import br.tec.wrcoder.meucondominio.core.storage.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
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

fun createHttpClient(tokenStore: TokenStore): HttpClient = httpEngineClient {
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
        logger = object : Logger {
            override fun log(message: String) {
                println("[Ktor] $message")
            }
        }
        level = LogLevel.ALL
    }
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
                val path = req.url.buildString()
                !path.contains("/auth/login") &&
                    !path.contains("/auth/refresh") &&
                    !path.contains("/auth/register-condominium") &&
                    !path.contains("/auth/join-condominium")
            }
        }
    }
    defaultRequest {
        url(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
        contentType(ContentType.Application.Json)
    }
}
