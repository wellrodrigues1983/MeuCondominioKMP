package br.tec.wrcoder.meucondominio.core.network

import kotlinx.coroutines.flow.StateFlow

interface NetworkMonitor {
    val isOnline: StateFlow<Boolean>
}

expect fun createNetworkMonitor(): NetworkMonitor
