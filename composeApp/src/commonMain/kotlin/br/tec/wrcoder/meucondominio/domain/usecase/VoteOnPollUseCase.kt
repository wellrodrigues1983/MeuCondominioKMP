package br.tec.wrcoder.meucondominio.domain.usecase

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.domain.model.Poll
import br.tec.wrcoder.meucondominio.domain.model.PollStatus
import br.tec.wrcoder.meucondominio.domain.repository.PollsRepository

class VoteOnPollUseCase(
    private val polls: PollsRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(poll: Poll, optionId: String, userId: String): AppResult<Unit> {
        val now = clock.now()
        if (poll.status != PollStatus.OPEN || now < poll.startsAt || now > poll.endsAt) {
            return AppError.Validation("Enquete não está aberta para votação").asFailure()
        }
        if (poll.options.none { it.id == optionId }) {
            return AppError.Validation("Opção inválida").asFailure()
        }
        if (polls.hasVoted(poll.id, userId)) {
            return AppError.Forbidden("Você já votou nesta enquete").asFailure()
        }
        return polls.vote(poll.id, optionId, userId)
    }
}
