package br.tec.wrcoder.meucondominio.di

import android.content.Context
import br.tec.wrcoder.meucondominio.data.local.db.AndroidDatabaseDriverFactory
import br.tec.wrcoder.meucondominio.data.local.db.DatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidModule(context: Context): Module = module {
    single<Context> { context }
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(context) }
}
