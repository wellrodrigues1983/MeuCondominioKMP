package br.tec.wrcoder.meucondominio.data.remote

import br.tec.wrcoder.meucondominio.core.storage.SecureStorage
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiConfig {
    const val BASE_URL = "https://api.meucondominio.example/v1"
    const val TOKEN_KEY = "auth_token"
}

expect fun httpEngineClient(config: HttpClientConfig<*>.() -> Unit): HttpClient

fun createHttpClient(storage: SecureStorage): HttpClient = httpEngineClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 20_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 20_000
    }
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.INFO
    }
    install(Auth) {
        bearer {
            loadTokens {
                storage.get(ApiConfig.TOKEN_KEY)?.let { BearerTokens(it, null) }
            }
            refreshTokens { null }
        }
    }
    defaultRequest {
        url(ApiConfig.BASE_URL)
        contentType(ContentType.Application.Json)
    }
}
