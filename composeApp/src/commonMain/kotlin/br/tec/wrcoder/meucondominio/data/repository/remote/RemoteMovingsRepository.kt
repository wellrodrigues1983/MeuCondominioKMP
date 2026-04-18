package br.tec.wrcoder.meucondominio.data.repository.remote

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import br.tec.wrcoder.meucondominio.data.mapper.toEpoch
import br.tec.wrcoder.meucondominio.data.remote.MovingsApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateMovingRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.DecisionRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.MovingDto
import br.tec.wrcoder.meucondominio.data.sync.Entities
import br.tec.wrcoder.meucondominio.data.sync.Ops
import br.tec.wrcoder.meucondominio.data.sync.OutboxDispatcher
import br.tec.wrcoder.meucondominio.data.sync.SyncCursors
import br.tec.wrcoder.meucondominio.domain.model.MovingRequest
import br.tec.wrcoder.meucondominio.domain.model.MovingStatus
import br.tec.wrcoder.meucondominio.domain.repository.MovingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CreatePayload(
    val id: String, val condominiumId: String, val unitId: String, val scheduledFor: String,
)

@Serializable
private data class DecisionPayload(val id: String, val reason: String?)

class RemoteMovingsRepository(
    private val db: MeuCondominioDb,
    private val api: MovingsApiService,
    private val dispatcher: OutboxDispatcher,
    private val network: NetworkMonitor,
    private val clock: AppClock,
    private val json: Json,
) : MovingRepository {

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reconciled = mutableSetOf<String>()

    init {
        dispatcher.register(Entities.MOVING, Ops.CREATE) { payload, _ ->
            val p = json.decodeFromString(CreatePayload.serializer(), payload)
            persist(api.create(p.condominiumId, CreateMovingRequestDto(p.unitId, p.scheduledFor)))
        }
        dispatcher.register(Entities.MOVING, Ops.APPROVE) { payload, _ ->
            val p = json.decodeFromString(DecisionPayload.serializer(), payload)
            persist(api.approve(p.id))
        }
        dispatcher.register(Entities.MOVING, Ops.REJECT) { payload, _ ->
            val p = json.decodeFromString(DecisionPayload.serializer(), payload)
            persist(api.reject(p.id, DecisionRequestDto(p.reason)))
        }
        dispatcher.register(Entities.MOVING, Ops.CANCEL_STAFF) { payload, _ ->
            val p = json.decodeFromString(DecisionPayload.serializer(), payload)
            persist(api.cancelByStaff(p.id, DecisionRequestDto(p.reason)))
        }
    }

    override fun observeByCondominium(condominiumId: String): Flow<List<MovingRequest>> =
        db.movingQueries.observeByCondominium(condominiumId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
            .onStart { bgScope.launch { pull(condominiumId) } }

    override fun observeByUnit(unitId: String): Flow<List<MovingRequest>> =
        db.movingQueries.observeByUnit(unitId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun request(
        condominiumId: String, unitId: String, residentUserId: String, scheduledFor: LocalDateTime,
    ): AppResult<MovingRequest> {
        val id = newId()
        val now = clock.now()
        val unit = db.condominiumQueries.getUnit(unitId).executeAsOneOrNull()
        val user = db.userQueries.getUser(residentUserId).executeAsOneOrNull()
        db.movingQueries.upsert(
            id = id, condominiumId = condominiumId, unitId = unitId,
            unitIdentifier = unit?.identifier ?: "",
            residentUserId = residentUserId, residentName = user?.name ?: "",
            scheduledForIso = scheduledFor.toString(), status = "PENDING",
            createdAt = now.toEpoch(), decisionReason = null, decidedByUserId = null,
            decidedAt = null, updatedAt = now.toEpoch(), version = 0, deleted = 0,
        )
        dispatcher.enqueue(Entities.MOVING, Ops.CREATE, id,
            json.encodeToString(CreatePayload.serializer(),
                CreatePayload(id, condominiumId, unitId, scheduledFor.toString())))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(db.movingQueries.get(id).executeAsOne().toDomain())
    }

    override suspend fun approve(id: String, staffUserId: String): AppResult<MovingRequest> =
        decide(id, "APPROVED", staffUserId, null, Ops.APPROVE)

    override suspend fun reject(id: String, staffUserId: String, reason: String): AppResult<MovingRequest> =
        decide(id, "REJECTED", staffUserId, reason, Ops.REJECT)

    override suspend fun cancelByStaff(id: String, staffUserId: String, reason: String): AppResult<MovingRequest> =
        decide(id, "CANCELLED", staffUserId, reason, Ops.CANCEL_STAFF)

    private suspend fun decide(
        id: String, status: String, staffUserId: String, reason: String?, op: String,
    ): AppResult<MovingRequest> {
        val row = db.movingQueries.get(id).executeAsOneOrNull()
            ?: return AppResult.Failure(AppError.NotFound("Mudança não encontrada"))
        val now = clock.now().toEpoch()
        db.movingQueries.upsert(
            id = row.id, condominiumId = row.condominiumId, unitId = row.unitId,
            unitIdentifier = row.unitIdentifier, residentUserId = row.residentUserId,
            residentName = row.residentName, scheduledForIso = row.scheduledForIso,
            status = status, createdAt = row.createdAt, decisionReason = reason,
            decidedByUserId = staffUserId, decidedAt = now, updatedAt = now,
            version = row.version, deleted = 0,
        )
        dispatcher.enqueue(Entities.MOVING, op, id,
            json.encodeToString(DecisionPayload.serializer(), DecisionPayload(id, reason)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(db.movingQueries.get(id).executeAsOne().toDomain())
    }

    private suspend fun pull(condominiumId: String) {
        if (!network.isOnline.value) return
        val firstTime = condominiumId !in reconciled
        val since = if (firstTime) null
        else db.syncMetadataQueries.getCursor(SyncCursors.movingsOf(condominiumId))
            .executeAsOneOrNull()?.let { Instant.fromEpochMilliseconds(it).toString() }
        runCatching { api.listByCondominium(condominiumId, since) }.onSuccess { items ->
            if (firstTime) {
                val serverIds = items.map { it.id }.toSet()
                val localIds = db.movingQueries.idsByCondominium(condominiumId).executeAsList().toSet()
                (localIds - serverIds).forEach { db.movingQueries.deleteById(it) }
                reconciled += condominiumId
            }
            items.forEach(::persist)
            items.maxOfOrNull { Instant.parse(it.updatedAt).toEpoch() }?.let {
                db.syncMetadataQueries.upsertCursor(SyncCursors.movingsOf(condominiumId), it)
            }
        }
    }

    private fun persist(dto: MovingDto) {
        db.movingQueries.upsert(
            id = dto.id, condominiumId = dto.condominiumId, unitId = dto.unitId,
            unitIdentifier = dto.unitIdentifier, residentUserId = dto.residentUserId,
            residentName = dto.residentName, scheduledForIso = dto.scheduledFor,
            status = dto.status, createdAt = Instant.parse(dto.createdAt).toEpoch(),
            decisionReason = dto.decisionReason, decidedByUserId = dto.decidedByUserId,
            decidedAt = dto.decidedAt?.let { Instant.parse(it).toEpoch() },
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
        )
    }

    private fun br.tec.wrcoder.meucondominio.data.local.db.Moving_entity.toDomain(): MovingRequest =
        MovingRequest(
            id = id, condominiumId = condominiumId, unitId = unitId,
            unitIdentifier = unitIdentifier, residentUserId = residentUserId,
            residentName = residentName, scheduledFor = LocalDateTime.parse(scheduledForIso),
            status = MovingStatus.valueOf(status),
            createdAt = Instant.fromEpochMilliseconds(createdAt),
            decisionReason = decisionReason, decidedByUserId = decidedByUserId,
            decidedAt = decidedAt?.let { Instant.fromEpochMilliseconds(it) },
        )
}
