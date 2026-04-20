package br.tec.wrcoder.meucondominio.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
data class TelemetryCrashRequestDto(
    val sessionId: String,
    val platform: String,
    val reason: String,
    val capturedAt: String,
    val entries: JsonArray,
)
