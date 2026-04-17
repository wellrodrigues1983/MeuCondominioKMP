package br.tec.wrcoder.meucondominio.data.local.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

class IosDatabaseDriverFactory : DatabaseDriverFactory {
    override fun create(): SqlDriver =
        NativeSqliteDriver(MeuCondominioDb.Schema, "meu_condominio.db")
}
