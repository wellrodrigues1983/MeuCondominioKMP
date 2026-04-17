package br.tec.wrcoder.meucondominio.data.local.db

import app.cash.sqldelight.db.SqlDriver

interface DatabaseDriverFactory {
    fun create(): SqlDriver
}
