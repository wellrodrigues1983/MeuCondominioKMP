package br.tec.wrcoder.meucondominio.domain.repository

import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.MovingRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

interface MovingRepository {
    fun observeByCondominium(condominiumId: String): Flow<List<MovingRequest>>
    fun observeByUnit(unitId: String): Flow<List<MovingRequest>>
    suspend fun request(
        condominiumId: String,
        unitId: String,
        residentUserId: String,
        scheduledFor: LocalDateTime,
    ): AppResult<MovingRequest>
    suspend fun approve(id: String, staffUserId: String): AppResult<MovingRequest>
    suspend fun reject(id: String, staffUserId: String, reason: String): AppResult<MovingRequest>
    suspend fun cancelByStaff(id: String, staffUserId: String, reason: String): AppResult<MovingRequest>
}
