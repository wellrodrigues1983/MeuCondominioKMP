package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.logging.AppLogger
import br.tec.wrcoder.meucondominio.domain.repository.NotificationsRepository

class LoggingNotificationsRepository : NotificationsRepository {
    private val log = AppLogger.withTag("Notify")
    override fun notify(title: String, body: String, data: Map<String, String>) {
        log.i { "$title — $body ${if (data.isEmpty()) "" else data}" }
    }
}
