package br.tec.wrcoder.meucondominio.data.sync

import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SyncEngine(
    private val dispatcher: OutboxDispatcher,
    private val network: NetworkMonitor,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val periodicIntervalMs: Long = 15_000,
) {
    fun start() {
        scope.launch {
            network.isOnline.collect { online ->
                if (online) runCatching { dispatcher.drain() }
            }
        }
        scope.launch {
            while (true) {
                delay(periodicIntervalMs)
                if (network.isOnline.value) runCatching { dispatcher.drain() }
            }
        }
    }
}
