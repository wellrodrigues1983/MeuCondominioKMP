package br.tec.wrcoder.meucondominio.core.logging

import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class AuditExporter(private val db: MeuCondominioDb, private val json: Json) {

    fun exportRecentAsJson(limit: Long = 500): String =
        json.encodeToString(JsonArray.serializer(), exportRecent(limit))

    fun exportRecent(limit: Long = 500): JsonArray =
        JsonArray(db.auditLogQueries.recent(limit).executeAsList().map { it.toJson() })

    fun exportSession(sessionId: String): JsonArray =
        JsonArray(db.auditLogQueries.bySession(sessionId).executeAsList().map { it.toJson() })

    fun sessionsWithCrashes(): List<String> =
        db.auditLogQueries.sessionsWithCrashes().executeAsList()

    fun clear() = db.auditLogQueries.clear()

    private fun br.tec.wrcoder.meucondominio.data.local.db.Audit_log_entity.toJson(): JsonObject =
        JsonObject(mapOf(
            "id" to JsonPrimitive(id),
            "timestamp" to JsonPrimitive(timestamp),
            "level" to JsonPrimitive(level),
            "tag" to JsonPrimitive(tag),
            "message" to JsonPrimitive(message),
            "throwable" to (throwable?.let { JsonPrimitive(it) } ?: JsonNull),
            "sessionId" to JsonPrimitive(sessionId),
            "userId" to (userId?.let { JsonPrimitive(it) } ?: JsonNull),
        ))
}
