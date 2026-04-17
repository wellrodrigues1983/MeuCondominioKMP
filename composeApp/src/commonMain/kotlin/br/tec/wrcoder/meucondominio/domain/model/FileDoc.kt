package br.tec.wrcoder.meucondominio.domain.model

import kotlinx.datetime.Instant

data class FileDoc(
    val id: String,
    val condominiumId: String,
    val title: String,
    val description: String? = null,
    val fileUrl: String,
    val sizeBytes: Long,
    val uploadedByUserId: String,
    val uploadedAt: Instant,
)
