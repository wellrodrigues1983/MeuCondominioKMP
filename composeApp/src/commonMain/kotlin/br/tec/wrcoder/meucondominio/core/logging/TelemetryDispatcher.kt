package br.tec.wrcoder.meucondominio.core.logging

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import br.tec.wrcoder.meucondominio.data.remote.TelemetryApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.TelemetryCrashRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

class TelemetryDispatcher(
    private val db: MeuCondominioDb,
    private val api: TelemetryApiService,
    private val network: NetworkMonitor,
    private val exporter: AuditExporter,
    private val session: SessionInfo,
    private val clock: AppClock,
    private val json: Json,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val log = AppLogger.withTag("Telemetry")
    private val drainMutex = Mutex()
    private var drainJob: Job? = null

    fun start() {
        scope.launch {
            runCatching { enqueuePendingCrashes() }
                .onFailure { log.w(it) { "enqueuePendingCrashes failed" } }
            drainSoon()
        }
        scope.launch {
            network.isOnline.filter { it }.collect { drainSoon() }
        }
    }

    fun enqueuePendingCrashes() {
        val sessionIds = exporter.sessionsWithCrashes()
        if (sessionIds.isEmpty()) return
        val now = clock.now().toEpochMilliseconds()
        sessionIds.forEach { sid ->
            if (sid == session.sessionId) return@forEach
            val already = db.telemetryOutboxQueries.existsForSession(sid).executeAsOne()
            if (already > 0) return@forEach
            val entries = exporter.exportSession(sid)
            val payload = json.encodeToString(JsonArray.serializer(), entries)
            db.telemetryOutboxQueries.enqueue(sid, REASON_CRASH, payload, now)
            log.i { "enqueued crash telemetry session=$sid entries=${entries.size}" }
        }
    }

    private fun drainSoon() {
        if (drainJob?.isActive == true) return
        drainJob = scope.launch {
            drainMutex.withLock { drainLoop() }
        }
    }

    private suspend fun drainLoop() {
        var attempt = 0
        while (scope.isActive) {
            if (!network.isOnline.value) return
            val batch = db.telemetryOutboxQueries.nextBatch(BATCH_SIZE).executeAsList()
            if (batch.isEmpty()) return
            for (row in batch) {
                val entries = runCatching {
                    json.parseToJsonElement(row.payload) as JsonArray
                }.getOrNull() ?: JsonArray(emptyList())
                val request = TelemetryCrashRequestDto(
                    sessionId = row.sessionId,
                    platform = platformName,
                    reason = row.reason,
                    capturedAt = Instant.fromEpochMilliseconds(row.createdAt).toString(),
                    entries = entries,
                )
                val result = runCatching { api.submitCrash(request) }
                val now = clock.now().toEpochMilliseconds()
                if (result.isSuccess) {
                    db.telemetryOutboxQueries.deleteById(row.id)
                    log.i { "uploaded telemetry session=${row.sessionId}" }
                    attempt = 0
                } else {
                    db.telemetryOutboxQueries.markAttempt(now, row.id)
                    log.w(result.exceptionOrNull()) { "upload failed session=${row.sessionId} attempts=${row.attempts + 1}" }
                    delay(backoff(++attempt))
                    return
                }
            }
        }
    }

    private fun backoff(attempt: Int): Long {
        val capped = attempt.coerceIn(1, 6)
        return (1_000L * (1L shl (capped - 1))).coerceAtMost(30_000L)
    }

    companion object {
        private const val REASON_CRASH = "crash"
        private const val BATCH_SIZE = 10L
    }
}
