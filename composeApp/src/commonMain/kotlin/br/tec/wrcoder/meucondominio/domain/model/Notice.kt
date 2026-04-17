package br.tec.wrcoder.meucondominio.domain.model

import kotlinx.datetime.Instant

data class Notice(
    val id: String,
    val condominiumId: String,
    val title: String,
    val description: String,
    val authorId: String,
    val authorName: String,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
)
