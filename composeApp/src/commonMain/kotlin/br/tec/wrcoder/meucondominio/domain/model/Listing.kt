package br.tec.wrcoder.meucondominio.domain.model

import kotlinx.datetime.Instant

enum class ListingStatus { ACTIVE, CLOSED, EXPIRED }

data class Listing(
    val id: String,
    val condominiumId: String,
    val authorUserId: String,
    val authorName: String,
    val unitIdentifier: String,
    val title: String,
    val description: String,
    val price: Double?,
    val imageUrls: List<String> = emptyList(),
    val status: ListingStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
)
