package br.tec.wrcoder.meucondominio.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime

enum class MovingStatus { PENDING, APPROVED, REJECTED, CANCELLED }

data class MovingRequest(
    val id: String,
    val condominiumId: String,
    val unitId: String,
    val unitIdentifier: String,
    val residentUserId: String,
    val residentName: String,
    val scheduledFor: LocalDateTime,
    val status: MovingStatus,
    val createdAt: Instant,
    val decisionReason: String? = null,
    val decidedByUserId: String? = null,
    val decidedAt: Instant? = null,
)
