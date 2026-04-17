package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.core.asSuccess
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.domain.model.MovingRequest
import br.tec.wrcoder.meucondominio.domain.model.MovingStatus
import br.tec.wrcoder.meucondominio.domain.repository.MovingRepository
import br.tec.wrcoder.meucondominio.domain.repository.NotificationsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime

class FakeMovingRepository(
    private val store: InMemoryStore,
    private val clock: AppClock,
    private val notifications: NotificationsRepository,
) : MovingRepository {

    override fun observeByCondominium(condominiumId: String): Flow<List<MovingRequest>> =
        store.movings.map { all ->
            all.filter { it.condominiumId == condominiumId }.sortedByDescending { it.createdAt }
        }

    override fun observeByUnit(unitId: String): Flow<List<MovingRequest>> =
        store.movings.map { all ->
            all.filter { it.unitId == unitId }.sortedByDescending { it.createdAt }
        }

    override suspend fun request(
        condominiumId: String,
        unitId: String,
        residentUserId: String,
        scheduledFor: LocalDateTime,
    ): AppResult<MovingRequest> {
        val unit = store.units.value.firstOrNull { it.id == unitId }
            ?: return AppError.NotFound("Unidade não encontrada").asFailure()
        val resident = store.users.value.firstOrNull { it.id == residentUserId }
            ?: return AppError.NotFound("Usuário não encontrado").asFailure()
        val req = MovingRequest(
            id = newId(),
            condominiumId = condominiumId,
            unitId = unitId,
            unitIdentifier = unit.identifier,
            residentUserId = residentUserId,
            residentName = resident.name,
            scheduledFor = scheduledFor,
            status = MovingStatus.PENDING,
            createdAt = clock.now(),
        )
        store.movings.value = store.movings.value + req
        notifications.notify("Nova solicitação de mudança", "Unidade ${unit.identifier} pediu agendamento")
        return req.asSuccess()
    }

    override suspend fun approve(id: String, staffUserId: String): AppResult<MovingRequest> =
        decide(id, staffUserId, MovingStatus.APPROVED, null, "aprovada")

    override suspend fun reject(id: String, staffUserId: String, reason: String): AppResult<MovingRequest> {
        if (reason.isBlank()) return AppError.Validation("Justificativa obrigatória").asFailure()
        return decide(id, staffUserId, MovingStatus.REJECTED, reason, "rejeitada")
    }

    override suspend fun cancelByStaff(id: String, staffUserId: String, reason: String): AppResult<MovingRequest> {
        if (reason.isBlank()) return AppError.Validation("Justificativa obrigatória").asFailure()
        return decide(id, staffUserId, MovingStatus.CANCELLED, reason, "cancelada")
    }

    private fun decide(
        id: String,
        staffUserId: String,
        status: MovingStatus,
        reason: String?,
        verb: String,
    ): AppResult<MovingRequest> {
        val current = store.movings.value.firstOrNull { it.id == id }
            ?: return AppError.NotFound("Solicitação não encontrada").asFailure()
        val updated = current.copy(
            status = status,
            decisionReason = reason,
            decidedByUserId = staffUserId,
            decidedAt = clock.now(),
        )
        store.movings.value = store.movings.value.map { if (it.id == id) updated else it }
        notifications.notify("Solicitação de mudança $verb", "Unidade ${current.unitIdentifier}")
        return updated.asSuccess()
    }
}
