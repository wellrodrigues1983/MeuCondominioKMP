package br.tec.wrcoder.meucondominio.domain.model

import kotlinx.datetime.Instant

enum class PollStatus { SCHEDULED, OPEN, CLOSED, CANCELLED }

data class PollOption(val id: String, val text: String)

data class Poll(
    val id: String,
    val condominiumId: String,
    val question: String,
    val options: List<PollOption>,
    val startsAt: Instant,
    val endsAt: Instant,
    val status: PollStatus,
    val createdByUserId: String,
    val createdAt: Instant,
)

data class PollVote(
    val pollId: String,
    val optionId: String,
    val userId: String,
    val castedAt: Instant,
)

data class PollResults(
    val pollId: String,
    val total: Int,
    val countsByOptionId: Map<String, Int>,
)
