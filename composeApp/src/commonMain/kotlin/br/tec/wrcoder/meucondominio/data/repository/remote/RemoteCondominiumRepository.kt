package br.tec.wrcoder.meucondominio.data.repository.remote

import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import br.tec.wrcoder.meucondominio.data.mapper.toDomain
import br.tec.wrcoder.meucondominio.data.mapper.toEpoch
import br.tec.wrcoder.meucondominio.data.remote.CondominiumApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.CondoUnitDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CondominiumDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateUnitRequestDto
import br.tec.wrcoder.meucondominio.domain.model.Condominium
import br.tec.wrcoder.meucondominio.domain.model.CondoUnit
import br.tec.wrcoder.meucondominio.domain.repository.CondominiumRepository
import kotlinx.datetime.Instant

class RemoteCondominiumRepository(
    private val api: CondominiumApiService,
    private val db: MeuCondominioDb,
    private val network: NetworkMonitor,
) : CondominiumRepository {

    override suspend fun findByCode(code: String): AppResult<Condominium> {
        if (network.isOnline.value) {
            val r = runRemote { api.byCode(code) }
            if (r is AppResult.Success) { persist(r.data); return AppResult.Success(r.data.toDomain()) }
        }
        val cached = db.condominiumQueries.getCondominiumByCode(code).executeAsOneOrNull()
            ?: return AppResult.Failure(AppError.NotFound("Condomínio não encontrado"))
        return AppResult.Success(cached.toDomain())
    }

    override suspend fun get(id: String): AppResult<Condominium> {
        if (network.isOnline.value) {
            val r = runRemote { api.get(id) }
            if (r is AppResult.Success) { persist(r.data); return AppResult.Success(r.data.toDomain()) }
        }
        val cached = db.condominiumQueries.getCondominium(id).executeAsOneOrNull()
            ?: return AppResult.Failure(AppError.NotFound("Condomínio não encontrado"))
        return AppResult.Success(cached.toDomain())
    }

    override suspend fun listUnits(condominiumId: String): AppResult<List<CondoUnit>> {
        if (network.isOnline.value) {
            val r = runRemote { api.listUnits(condominiumId) }
            if (r is AppResult.Success) {
                r.data.forEach(::persistUnit)
                return AppResult.Success(r.data.map { it.toDomain() })
            }
        }
        return AppResult.Success(
            db.condominiumQueries.listUnits(condominiumId).executeAsList().map { it.toUnitDomain() }
        )
    }

    override suspend fun findUnitByIdentifier(
        condominiumId: String, identifier: String,
    ): AppResult<CondoUnit> {
        if (network.isOnline.value) {
            val r = runRemote { api.findUnitByIdentifier(condominiumId, identifier) }
            if (r is AppResult.Success) { persistUnit(r.data); return AppResult.Success(r.data.toDomain()) }
        }
        val cached = db.condominiumQueries.findUnitByIdentifier(condominiumId, identifier).executeAsOneOrNull()
            ?: return AppResult.Failure(AppError.NotFound("Unidade não encontrada"))
        return AppResult.Success(cached.toUnitDomain())
    }

    override suspend fun createUnit(
        condominiumId: String, identifier: String, block: String?,
    ): AppResult<CondoUnit> =
        runRemote {
            val dto = api.createUnit(condominiumId, CreateUnitRequestDto(identifier, block))
            persistUnit(dto)
            dto.toDomain()
        }

    private fun persist(dto: CondominiumDto) {
        db.condominiumQueries.upsertCondominium(
            id = dto.id, name = dto.name, address = dto.address, code = dto.code,
            createdAt = Instant.parse(dto.createdAt).toEpoch(),
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
        )
    }

    private fun persistUnit(dto: CondoUnitDto) {
        db.condominiumQueries.upsertUnit(
            id = dto.id, condominiumId = dto.condominiumId, identifier = dto.identifier,
            block = dto.block, ownerUserId = dto.ownerUserId,
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
        )
    }

    private fun br.tec.wrcoder.meucondominio.data.local.db.Condominium_entity.toDomain(): Condominium =
        Condominium(id = id, name = name, address = address, code = code,
            createdAt = Instant.fromEpochMilliseconds(createdAt))

    private fun br.tec.wrcoder.meucondominio.data.local.db.Unit_entity.toUnitDomain(): CondoUnit =
        CondoUnit(id = id, condominiumId = condominiumId, identifier = identifier,
            block = block, ownerUserId = ownerUserId)
}
