package br.tec.wrcoder.meucondominio.domain.repository

import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.Poll
import br.tec.wrcoder.meucondominio.domain.model.PollResults
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface PollsRepository {
    fun observe(condominiumId: String): Flow<List<Poll>>
    suspend fun create(
        condominiumId: String,
        question: String,
        options: List<String>,
        startsAt: Instant,
        endsAt: Instant,
        createdByUserId: String,
    ): AppResult<Poll>
    suspend fun cancel(pollId: String): AppResult<Poll>
    suspend fun vote(pollId: String, optionId: String, userId: String): AppResult<Unit>
    suspend fun hasVoted(pollId: String, userId: String): Boolean
    suspend fun results(pollId: String): AppResult<PollResults>
}
