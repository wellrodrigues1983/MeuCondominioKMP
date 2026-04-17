package br.tec.wrcoder.meucondominio.domain.model

import kotlinx.datetime.Instant

enum class PackageStatus { RECEIVED, PICKED_UP }

data class PackageItem(
    val id: String,
    val condominiumId: String,
    val unitId: String,
    val unitIdentifier: String,
    val description: String,
    val carrier: String? = null,
    val status: PackageStatus,
    val receivedAt: Instant,
    val pickedUpAt: Instant? = null,
    val registeredByUserId: String,
)
