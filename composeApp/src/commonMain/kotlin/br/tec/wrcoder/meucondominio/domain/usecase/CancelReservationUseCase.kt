package br.tec.wrcoder.meucondominio.domain.usecase

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.domain.model.Reservation
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import br.tec.wrcoder.meucondominio.domain.repository.SpaceRepository
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Regra: morador só pode cancelar até 7 dias antes da reserva.
 * Após esse prazo: apenas admin/supervisor com justificativa.
 */
class CancelReservationUseCase(
    private val spaceRepository: SpaceRepository,
    private val clock: AppClock,
) {
    suspend operator fun invoke(
        reservation: Reservation,
        actor: User,
        reason: String? = null,
    ): AppResult<Reservation> {
        val today = clock.now().toLocalDateTime(clock.timeZone()).date
        val cutoff = reservation.date.minus(DatePeriod(days = 7))
        val withinResidentWindow = today <= cutoff

        return when (actor.role) {
            UserRole.RESIDENT -> {
                if (actor.id != reservation.residentUserId) {
                    AppError.Forbidden("Somente o morador que reservou pode cancelar").asFailure()
                } else if (!withinResidentWindow) {
                    AppError.Forbidden("Prazo de cancelamento pelo morador expirou (até 7 dias antes)").asFailure()
                } else {
                    spaceRepository.cancelByResident(reservation.id, actor.id)
                }
            }
            UserRole.ADMIN, UserRole.SUPERVISOR -> {
                if (reason.isNullOrBlank()) {
                    AppError.Validation("Informe a justificativa do cancelamento").asFailure()
                } else {
                    spaceRepository.cancelByStaff(reservation.id, actor.id, reason)
                }
            }
        }
    }
}
