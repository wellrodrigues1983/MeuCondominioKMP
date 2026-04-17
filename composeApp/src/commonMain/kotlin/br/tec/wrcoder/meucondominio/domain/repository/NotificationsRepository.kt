package br.tec.wrcoder.meucondominio.domain.repository

/** Platform-side push/local notifications bridge. In this scaffold we keep a single entry point. */
interface NotificationsRepository {
    fun notify(title: String, body: String, data: Map<String, String> = emptyMap())
}
