package br.tec.wrcoder.meucondominio.domain.repository

import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.Condominium
import br.tec.wrcoder.meucondominio.domain.model.CondoUnit

interface CondominiumRepository {
    suspend fun findByCode(code: String): AppResult<Condominium>
    suspend fun get(id: String): AppResult<Condominium>
    suspend fun listUnits(condominiumId: String): AppResult<List<CondoUnit>>
    suspend fun findUnitByIdentifier(condominiumId: String, identifier: String): AppResult<CondoUnit>
    suspend fun createUnit(condominiumId: String, identifier: String, block: String?): AppResult<CondoUnit>
}
