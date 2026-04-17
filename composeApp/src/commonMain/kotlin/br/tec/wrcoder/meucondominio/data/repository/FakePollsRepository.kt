package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.core.asSuccess
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.domain.model.Poll
import br.tec.wrcoder.meucondominio.domain.model.PollOption
import br.tec.wrcoder.meucondominio.domain.model.PollResults
import br.tec.wrcoder.meucondominio.domain.model.PollStatus
import br.tec.wrcoder.meucondominio.domain.model.PollVote
import br.tec.wrcoder.meucondominio.domain.repository.PollsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

class FakePollsRepository(
    private val store: InMemoryStore,
    private val clock: AppClock,
) : PollsRepository {

    override fun observe(condominiumId: String): Flow<List<Poll>> =
        store.polls.map { all ->
            val now = clock.now()
            all.filter { it.condominiumId == condominiumId }
                .map { poll ->
                    val effective = when {
                        poll.status == PollStatus.CANCELLED -> PollStatus.CANCELLED
                        now < poll.startsAt -> PollStatus.SCHEDULED
                        now > poll.endsAt -> PollStatus.CLOSED
                        else -> PollStatus.OPEN
                    }
                    if (effective != poll.status) poll.copy(status = effective) else poll
                }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun create(
        condominiumId: String,
        question: String,
        options: List<String>,
        startsAt: Instant,
        endsAt: Instant,
        createdByUserId: String,
    ): AppResult<Poll> {
        if (question.isBlank()) return AppError.Validation("Informe a pergunta").asFailure()
        if (options.size < 2) return AppError.Validation("Adicione ao menos 2 opções").asFailure()
        if (endsAt <= startsAt) return AppError.Validation("Data final deve ser após o início").asFailure()
        val poll = Poll(
            id = newId(),
            condominiumId = condominiumId,
            question = question.trim(),
            options = options.map { PollOption(newId(), it.trim()) },
            startsAt = startsAt,
            endsAt = endsAt,
            status = if (clock.now() >= startsAt) PollStatus.OPEN else PollStatus.SCHEDULED,
            createdByUserId = createdByUserId,
            createdAt = clock.now(),
        )
        store.polls.value = store.polls.value + poll
        return poll.asSuccess()
    }

    override suspend fun cancel(pollId: String): AppResult<Poll> {
        val current = store.polls.value.firstOrNull { it.id == pollId }
            ?: return AppError.NotFound("Enquete não encontrada").asFailure()
        val updated = current.copy(status = PollStatus.CANCELLED)
        store.polls.value = store.polls.value.map { if (it.id == pollId) updated else it }
        return updated.asSuccess()
    }

    override suspend fun vote(pollId: String, optionId: String, userId: String): AppResult<Unit> {
        if (hasVoted(pollId, userId)) return AppError.Forbidden("Você já votou").asFailure()
        val vote = PollVote(pollId, optionId, userId, clock.now())
        store.pollVotes.value = store.pollVotes.value + vote
        return Unit.asSuccess()
    }

    override suspend fun hasVoted(pollId: String, userId: String): Boolean =
        store.pollVotes.value.any { it.pollId == pollId && it.userId == userId }

    override suspend fun results(pollId: String): AppResult<PollResults> {
        val votes = store.pollVotes.value.filter { it.pollId == pollId }
        val counts = votes.groupingBy { it.optionId }.eachCount()
        return PollResults(pollId, votes.size, counts).asSuccess()
    }
}
