package br.tec.wrcoder.meucondominio.core.logging

actual fun installCrashHandler(writer: LocalAuditLogWriter) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching { writer.writeCrashBlocking(throwable, tag = "Crash-${thread.name}") }
        previous?.uncaughtException(thread, throwable)
    }
}
