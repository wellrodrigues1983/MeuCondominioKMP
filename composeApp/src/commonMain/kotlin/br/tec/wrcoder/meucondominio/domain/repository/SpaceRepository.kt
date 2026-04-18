package br.tec.wrcoder.meucondominio.domain.repository

import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.CommonSpace
import br.tec.wrcoder.meucondominio.domain.model.Reservation
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface SpaceRepository {
    fun observeSpaces(condominiumId: String): Flow<List<CommonSpace>>
    suspend fun refreshSpaces(condominiumId: String)
    suspend fun createSpace(
        condominiumId: String,
        name: String,
        description: String,
        price: Double,
        imageUrls: List<String>,
    ): AppResult<CommonSpace>
    suspend fun updateSpace(space: CommonSpace): AppResult<CommonSpace>
    suspend fun deleteSpace(id: String): AppResult<Unit>

    fun observeReservations(spaceId: String): Flow<List<Reservation>>
    fun observeReservationsByUnit(unitId: String): Flow<List<Reservation>>
    suspend fun reserve(
        spaceId: String,
        unitId: String,
        residentUserId: String,
        date: LocalDate,
    ): AppResult<Reservation>
    suspend fun cancelByResident(reservationId: String, residentUserId: String): AppResult<Reservation>
    suspend fun cancelByStaff(reservationId: String, staffUserId: String, reason: String): AppResult<Reservation>
}
