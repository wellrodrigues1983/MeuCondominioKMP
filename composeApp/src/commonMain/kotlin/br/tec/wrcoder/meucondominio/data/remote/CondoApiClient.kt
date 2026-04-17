package br.tec.wrcoder.meucondominio.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText

/**
 * Thin template for the REST layer. Real repositories should use it and map
 * DTOs into domain models. Kept minimal on purpose — the in-memory fakes are
 * the source of truth in this scaffold.
 */
class CondoApiClient(@PublishedApi internal val http: HttpClient) {
    suspend inline fun <reified R> getJson(path: String): R = http.get(path).body()
    suspend inline fun <reified B, reified R> postJson(path: String, body: B): R =
        http.post(path) { setBody(body) }.body()
    suspend fun rawGet(path: String): String = http.get(path).bodyAsText()
}
