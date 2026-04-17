package br.tec.wrcoder.meucondominio.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class CommonSpace(
    val id: String,
    val condominiumId: String,
    val name: String,
    val description: String,
    val price: Double,
    val imageUrls: List<String> = emptyList(),
    val active: Boolean = true,
)

enum class ReservationStatus { CONFIRMED, CANCELLED_BY_RESIDENT, CANCELLED_BY_STAFF }

data class Reservation(
    val id: String,
    val spaceId: String,
    val spaceName: String,
    val unitId: String,
    val unitIdentifier: String,
    val residentUserId: String,
    val residentName: String,
    val date: LocalDate,
    val status: ReservationStatus,
    val createdAt: Instant,
    val cancelledAt: Instant? = null,
    val cancellationReason: String? = null,
)
