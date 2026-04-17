package br.tec.wrcoder.meucondominio.di

import br.tec.wrcoder.meucondominio.data.local.db.DatabaseDriverFactory
import br.tec.wrcoder.meucondominio.data.local.db.IosDatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

fun iosModule(): Module = module {
    single<DatabaseDriverFactory> { IosDatabaseDriverFactory() }
}

/** Convenience for iOS entry-point code (Swift) to bootstrap DI. */
fun doInitKoin() {
    initKoin(platformModules = listOf(iosModule()))
}
