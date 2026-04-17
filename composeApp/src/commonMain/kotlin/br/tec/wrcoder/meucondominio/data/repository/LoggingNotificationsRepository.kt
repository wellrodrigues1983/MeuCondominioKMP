package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.domain.repository.NotificationsRepository

/** Placeholder that logs to stdout. Real platforms should provide FCM / APNs bridges. */
class LoggingNotificationsRepository : NotificationsRepository {
    override fun notify(title: String, body: String, data: Map<String, String>) {
        println("[NOTIFY] $title — $body ${if (data.isEmpty()) "" else data}")
    }
}
