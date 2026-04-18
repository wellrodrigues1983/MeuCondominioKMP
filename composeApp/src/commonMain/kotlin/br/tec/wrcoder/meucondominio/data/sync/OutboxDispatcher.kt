package br.tec.wrcoder.meucondominio.data.sync

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb

typealias OutboxHandler = suspend (payloadJson: String, entityId: String) -> Unit

class OutboxDispatcher(
    private val db: MeuCondominioDb,
    private val clock: AppClock,
) {
    private val handlers = mutableMapOf<String, OutboxHandler>()

    fun register(entity: String, op: String, handler: OutboxHandler) {
        handlers["$entity.$op"] = handler
    }

    fun enqueue(entity: String, op: String, entityId: String, payloadJson: String) {
        db.outboxQueries.enqueue(entity, op, entityId, payloadJson, clock.now().toEpochMilliseconds())
    }

    suspend fun drain() {
        val now = clock.now().toEpochMilliseconds()
        val batch = db.outboxQueries.nextBatch(now, 50).executeAsList()
        for (entry in batch) {
            val key = "${entry.entity}.${entry.op}"
            val handler = handlers[key] ?: continue
            try {
                handler(entry.payloadJson, entry.entityId)
                db.outboxQueries.remove(entry.id)
            } catch (t: Throwable) {
                val backoffMs = backoffFor(entry.attempts)
                db.outboxQueries.markFailed(t.message ?: "error", now + backoffMs, entry.id)
            }
        }
    }

    fun pendingCount(): Long = db.outboxQueries.pendingCount().executeAsOne()

    private fun backoffFor(attempts: Long): Long {
        val capped = if (attempts > 6) 6 else attempts
        return (1L shl capped.toInt()) * 2_000L
    }
}
