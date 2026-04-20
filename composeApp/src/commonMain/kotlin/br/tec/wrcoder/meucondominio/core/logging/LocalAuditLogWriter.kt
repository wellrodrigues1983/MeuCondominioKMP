package br.tec.wrcoder.meucondominio.core.logging

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocalAuditLogWriter(
    private val db: MeuCondominioDb,
    private val session: SessionInfo,
    private val clock: AppClock,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : LogWriter() {

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val ts = clock.now().toEpochMilliseconds()
        val level = severity.name
        val msg = PiiSanitizer.scrub(message)
        val stack = throwable?.let { PiiSanitizer.scrub(it.stackTraceToString()) }
        val userId = session.currentUserId
        scope.launch {
            runCatching {
                db.auditLogQueries.insert(ts, level, tag, msg, stack, session.sessionId, userId)
            }
        }
    }

    fun writeCrashBlocking(throwable: Throwable, tag: String = "Crash") {
        val ts = clock.now().toEpochMilliseconds()
        val stack = PiiSanitizer.scrub(throwable.stackTraceToString())
        runCatching {
            db.auditLogQueries.insert(
                ts, "CRASH", tag, throwable.message ?: "uncaught", stack,
                session.sessionId, session.currentUserId,
            )
        }
    }
}
