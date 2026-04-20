package br.tec.wrcoder.meucondominio.di

import br.tec.wrcoder.meucondominio.core.logging.AppLogger
import br.tec.wrcoder.meucondominio.core.logging.LocalAuditLogWriter
import br.tec.wrcoder.meucondominio.core.logging.TelemetryDispatcher
import br.tec.wrcoder.meucondominio.core.logging.installCrashHandler
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

fun initKoin(
    platformModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication {
    val app = startKoin {
        appDeclaration()
        modules(platformModules + commonModule() + loggingModule())
    }
    val auditWriter = app.koin.get<LocalAuditLogWriter>()
    AppLogger.install(auditWriter)
    installCrashHandler(auditWriter)
    app.koin.get<TelemetryDispatcher>().start()
    return app
}
