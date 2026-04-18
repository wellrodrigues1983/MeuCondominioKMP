package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.core.asSuccess
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.domain.model.CommonSpace
import br.tec.wrcoder.meucondominio.domain.model.Reservation
import br.tec.wrcoder.meucondominio.domain.model.ReservationStatus
import br.tec.wrcoder.meucondominio.domain.repository.SpaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class FakeSpaceRepository(
    private val store: InMemoryStore,
    private val clock: AppClock,
) : SpaceRepository {

    override fun observeSpaces(condominiumId: String): Flow<List<CommonSpace>> =
        store.spaces.map { all -> all.filter { it.condominiumId == condominiumId && it.active } }

    override suspend fun refreshSpaces(condominiumId: String) { }

    override suspend fun createSpace(
        condominiumId: String,
        name: String,
        description: String,
        price: Double,
        imageUrls: List<String>,
    ): AppResult<CommonSpace> {
        if (name.isBlank() || description.isBlank()) {
            return AppError.Validation("Nome e descrição são obrigatórios").asFailure()
        }
        if (price < 0.0) return AppError.Validation("Valor inválido").asFailure()
        val space = CommonSpace(
            id = newId(),
            condominiumId = condominiumId,
            name = name.trim(),
            description = description.trim(),
            price = price,
            imageUrls = imageUrls,
        )
        store.spaces.value = store.spaces.value + space
        return space.asSuccess()
    }

    override suspend fun updateSpace(space: CommonSpace): AppResult<CommonSpace> {
        store.spaces.value = store.spaces.value.map { if (it.id == space.id) space else it }
        return space.asSuccess()
    }

    override suspend fun deleteSpace(id: String): AppResult<Unit> {
        store.spaces.value = store.spaces.value.map {
            if (it.id == id) it.copy(active = false) else it
        }
        return Unit.asSuccess()
    }

    override fun observeReservations(spaceId: String): Flow<List<Reservation>> =
        store.reservations.map { all -> all.filter { it.spaceId == spaceId }.sortedBy { it.date } }

    override fun observeReservationsByUnit(unitId: String): Flow<List<Reservation>> =
        store.reservations.map { all -> all.filter { it.unitId == unitId }.sortedBy { it.date } }

    override suspend fun reserve(
        spaceId: String,
        unitId: String,
        residentUserId: String,
        date: LocalDate,
    ): AppResult<Reservation> {
        val space = store.spaces.value.firstOrNull { it.id == spaceId && it.active }
            ?: return AppError.NotFound("Espaço não encontrado").asFailure()
        val taken = store.reservations.value.any {
            it.spaceId == spaceId && it.date == date && it.status == ReservationStatus.CONFIRMED
        }
        if (taken) return AppError.Validation("Data já reservada para este espaço").asFailure()

        val unit = store.units.value.firstOrNull { it.id == unitId }
            ?: return AppError.NotFound("Unidade não encontrada").asFailure()
        val user = store.users.value.firstOrNull { it.id == residentUserId }
            ?: return AppError.NotFound("Usuário não encontrado").asFailure()

        val reservation = Reservation(
            id = newId(),
            spaceId = spaceId,
            spaceName = space.name,
            unitId = unitId,
            unitIdentifier = unit.identifier,
            residentUserId = residentUserId,
            residentName = user.name,
            date = date,
            status = ReservationStatus.CONFIRMED,
            createdAt = clock.now(),
        )
        store.reservations.value = store.reservations.value + reservation
        return reservation.asSuccess()
    }

    override suspend fun cancelByResident(reservationId: String, residentUserId: String): AppResult<Reservation> =
        cancel(reservationId, residentUserId, ReservationStatus.CANCELLED_BY_RESIDENT, null)

    override suspend fun cancelByStaff(
        reservationId: String,
        staffUserId: String,
        reason: String,
    ): AppResult<Reservation> =
        cancel(reservationId, staffUserId, ReservationStatus.CANCELLED_BY_STAFF, reason)

    private fun cancel(id: String, userId: String, status: ReservationStatus, reason: String?): AppResult<Reservation> {
        val current = store.reservations.value.firstOrNull { it.id == id }
            ?: return AppError.NotFound("Reserva não encontrada").asFailure()
        val updated = current.copy(
            status = status,
            cancelledAt = clock.now(),
            cancellationReason = reason,
        )
        store.reservations.value = store.reservations.value.map { if (it.id == id) updated else it }
        return updated.asSuccess().also { @Suppress("UNUSED_EXPRESSION") userId }
    }
}
