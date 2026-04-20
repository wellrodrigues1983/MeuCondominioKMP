package br.tec.wrcoder.meucondominio.core.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter

object AppLogger {
    fun install(auditWriter: LocalAuditLogWriter, minSeverity: Severity = Severity.Info) {
        Logger.setMinSeverity(minSeverity)
        Logger.setLogWriters(platformLogWriter(), auditWriter)
    }

    fun withTag(tag: String): Logger = Logger.withTag(tag)
}
