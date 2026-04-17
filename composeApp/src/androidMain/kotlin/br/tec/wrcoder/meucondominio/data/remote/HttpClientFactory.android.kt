package br.tec.wrcoder.meucondominio.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp

actual fun httpEngineClient(config: HttpClientConfig<*>.() -> Unit): HttpClient =
    HttpClient(OkHttp, config)
