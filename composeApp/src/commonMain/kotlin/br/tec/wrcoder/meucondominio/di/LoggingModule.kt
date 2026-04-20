package br.tec.wrcoder.meucondominio.di

import br.tec.wrcoder.meucondominio.core.logging.AuditExporter
import br.tec.wrcoder.meucondominio.core.logging.LocalAuditLogWriter
import br.tec.wrcoder.meucondominio.core.logging.SessionInfo
import br.tec.wrcoder.meucondominio.core.logging.TelemetryDispatcher
import br.tec.wrcoder.meucondominio.data.remote.TelemetryApiService
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

fun loggingModule(): Module = module {
    singleOf(::SessionInfo)
    singleOf(::LocalAuditLogWriter)
    singleOf(::AuditExporter)
    single { TelemetryApiService(get()) }
    single { TelemetryDispatcher(get(), get(), get(), get(), get(), get(), get()) }
}
