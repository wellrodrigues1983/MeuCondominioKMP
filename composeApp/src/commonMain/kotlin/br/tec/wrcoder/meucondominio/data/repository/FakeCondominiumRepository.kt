package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.core.asSuccess
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.domain.model.Condominium
import br.tec.wrcoder.meucondominio.domain.model.CondoUnit
import br.tec.wrcoder.meucondominio.domain.repository.CondominiumRepository

class FakeCondominiumRepository(private val store: InMemoryStore) : CondominiumRepository {
    override suspend fun findByCode(code: String): AppResult<Condominium> =
        store.condominiums.value.firstOrNull { it.code.equals(code, true) }?.asSuccess()
            ?: AppError.NotFound("Condomínio não encontrado").asFailure()

    override suspend fun get(id: String): AppResult<Condominium> =
        store.condominiums.value.firstOrNull { it.id == id }?.asSuccess()
            ?: AppError.NotFound("Condomínio não encontrado").asFailure()

    override suspend fun listUnits(condominiumId: String): AppResult<List<CondoUnit>> =
        store.units.value.filter { it.condominiumId == condominiumId }.asSuccess()

    override suspend fun findUnitByIdentifier(condominiumId: String, identifier: String): AppResult<CondoUnit> =
        store.units.value.firstOrNull {
            it.condominiumId == condominiumId && it.identifier.equals(identifier, true)
        }?.asSuccess() ?: AppError.NotFound("Unidade não encontrada").asFailure()

    override suspend fun createUnit(condominiumId: String, identifier: String, block: String?): AppResult<CondoUnit> {
        if (store.units.value.any { it.condominiumId == condominiumId && it.identifier.equals(identifier, true) }) {
            return AppError.Validation("Unidade já existe").asFailure()
        }
        val unit = CondoUnit(
            id = newId(),
            condominiumId = condominiumId,
            identifier = identifier,
            block = block,
        )
        store.units.value = store.units.value + unit
        return unit.asSuccess()
    }
}
