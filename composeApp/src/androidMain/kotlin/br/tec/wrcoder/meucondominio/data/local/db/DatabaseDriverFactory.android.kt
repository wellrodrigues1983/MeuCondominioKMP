package br.tec.wrcoder.meucondominio.data.local.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

class AndroidDatabaseDriverFactory(private val context: Context) : DatabaseDriverFactory {
    override fun create(): SqlDriver =
        AndroidSqliteDriver(MeuCondominioDb.Schema, context, "meu_condominio.db")
}
