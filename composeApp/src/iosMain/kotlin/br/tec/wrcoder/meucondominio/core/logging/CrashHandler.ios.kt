@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.tec.wrcoder.meucondominio.core.logging

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.usePinned
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSException
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSSetUncaughtExceptionHandler
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.SIGABRT
import platform.posix.SIGBUS
import platform.posix.SIGFPE
import platform.posix.SIGILL
import platform.posix.SIGPIPE
import platform.posix.SIGSEGV
import platform.posix.SIG_DFL
import platform.posix.close
import platform.posix.open
import platform.posix.raise
import platform.posix.signal
import platform.posix.write

private var installedWriter: LocalAuditLogWriter? = null
private var markerPath: String? = null

actual fun installCrashHandler(writer: LocalAuditLogWriter) {
    installedWriter = writer

    val docsPath = (NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    ).firstOrNull() as? String) ?: return
    val path = "$docsPath/.crash_marker"
    markerPath = path

    importMarkerIfPresent(writer, path)

    NSSetUncaughtExceptionHandler(staticCFunction<NSException?, Unit> { exc ->
        val w = installedWriter ?: return@staticCFunction
        val name = exc?.name ?: "NSException"
        val reason = exc?.reason ?: "unknown"
        val stack = exc?.callStackSymbols?.joinToString("\n") ?: ""
        w.writeCrashBlocking(
            Throwable("$name: $reason\n$stack"),
            tag = "Crash-NSException",
        )
    })

    val signals = intArrayOf(SIGSEGV, SIGABRT, SIGBUS, SIGILL, SIGFPE, SIGPIPE)
    for (sig in signals) {
        signal(sig, staticCFunction<Int, Unit> { signum ->
            writeMarkerSigSafe(signum)
            signal(signum, SIG_DFL)
            raise(signum)
        })
    }
}

private fun writeMarkerSigSafe(signum: Int) {
    val path = markerPath ?: return
    val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, 0x180)
    if (fd < 0) return
    val bytes = "SIG=$signum\n".encodeToByteArray()
    bytes.usePinned { p ->
        write(fd, p.addressOf(0), bytes.size.convert())
    }
    close(fd)
}

private fun importMarkerIfPresent(writer: LocalAuditLogWriter, path: String) {
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(path)) return
    val content = NSString.stringWithContentsOfFile(
        path, encoding = NSUTF8StringEncoding, error = null,
    )?.toString()?.trim()
    fm.removeItemAtPath(path, error = null)
    if (content.isNullOrBlank()) return
    writer.writeCrashBlocking(
        Throwable("Native signal crash previous session: $content"),
        tag = "Crash-Signal",
    )
}
