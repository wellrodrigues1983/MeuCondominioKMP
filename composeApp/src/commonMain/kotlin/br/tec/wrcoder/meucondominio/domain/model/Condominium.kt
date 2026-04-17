package br.tec.wrcoder.meucondominio.domain.model

import kotlinx.datetime.Instant

data class Condominium(
    val id: String,
    val name: String,
    val address: String,
    val code: String,
    val createdAt: Instant,
)

data class CondoUnit(
    val id: String,
    val condominiumId: String,
    val identifier: String,
    val block: String? = null,
    val ownerUserId: String? = null,
)
