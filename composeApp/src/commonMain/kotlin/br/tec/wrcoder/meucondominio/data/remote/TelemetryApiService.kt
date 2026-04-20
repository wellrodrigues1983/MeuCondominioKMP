package br.tec.wrcoder.meucondominio.data.remote

import br.tec.wrcoder.meucondominio.data.remote.dto.TelemetryCrashRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class TelemetryApiService(private val http: HttpClient) {
    suspend fun submitCrash(body: TelemetryCrashRequestDto) {
        http.post("telemetry/crash") { setBody(body) }
    }
}
