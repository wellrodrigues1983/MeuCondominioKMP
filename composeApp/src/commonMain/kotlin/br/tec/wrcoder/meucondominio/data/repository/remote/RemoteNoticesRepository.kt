package br.tec.wrcoder.meucondominio.data.repository.remote

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import br.tec.wrcoder.meucondominio.data.mapper.iso
import br.tec.wrcoder.meucondominio.data.mapper.toDomain
import br.tec.wrcoder.meucondominio.data.mapper.toEpoch
import br.tec.wrcoder.meucondominio.data.remote.NoticesApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateNoticeRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.NoticeDto
import br.tec.wrcoder.meucondominio.data.remote.dto.UpdateNoticeRequestDto
import br.tec.wrcoder.meucondominio.data.sync.Entities
import br.tec.wrcoder.meucondominio.data.sync.Ops
import br.tec.wrcoder.meucondominio.data.sync.OutboxDispatcher
import br.tec.wrcoder.meucondominio.data.sync.SyncCursors
import br.tec.wrcoder.meucondominio.domain.model.Notice
import br.tec.wrcoder.meucondominio.domain.repository.NoticeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CreateNoticePayload(
    val id: String, val condominiumId: String, val title: String, val description: String,
)

@Serializable
private data class UpdateNoticePayload(val id: String, val title: String, val description: String)

@Serializable
private data class DeleteNoticePayload(val id: String)

class RemoteNoticesRepository(
    private val db: MeuCondominioDb,
    private val api: NoticesApiService,
    private val dispatcher: OutboxDispatcher,
    private val network: NetworkMonitor,
    private val clock: AppClock,
    private val json: Json,
) : NoticeRepository {

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        dispatcher.register(Entities.NOTICE, Ops.CREATE) { payload, _ ->
            val p = json.decodeFromString(CreateNoticePayload.serializer(), payload)
            val dto = api.create(p.condominiumId, CreateNoticeRequestDto(p.title, p.description))
            if (dto.id != p.id) db.noticeQueries.deleteById(p.id)
            persist(dto)
        }
        dispatcher.register(Entities.NOTICE, Ops.UPDATE) { payload, _ ->
            val p = json.decodeFromString(UpdateNoticePayload.serializer(), payload)
            val dto = api.update(p.id, UpdateNoticeRequestDto(p.title, p.description))
            persist(dto)
        }
        dispatcher.register(Entities.NOTICE, Ops.DELETE) { payload, _ ->
            val p = json.decodeFromString(DeleteNoticePayload.serializer(), payload)
            api.delete(p.id)
        }
    }

    override fun observe(condominiumId: String): Flow<List<Notice>> =
        db.noticeQueries.observe(condominiumId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
            .onStart { bgScope.launch { pull(condominiumId) } }
            .flowOn(Dispatchers.Default)

    override suspend fun create(
        condominiumId: String, authorId: String, authorName: String,
        title: String, description: String,
    ): AppResult<Notice> {
        val id = newId()
        val now = clock.now()
        val local = Notice(id, condominiumId, title, description, authorId, authorName, now, now)
        writeToDb(local)
        val payload = json.encodeToString(
            CreateNoticePayload.serializer(),
            CreateNoticePayload(id, condominiumId, title, description)
        )
        dispatcher.enqueue(Entities.NOTICE, Ops.CREATE, id, payload)
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(local)
    }

    override suspend fun update(id: String, title: String, description: String): AppResult<Notice> {
        val existing = db.noticeQueries.get(id).executeAsOneOrNull()?.toDomain()
            ?: return AppResult.Failure(br.tec.wrcoder.meucondominio.core.AppError.NotFound("Aviso não encontrado"))
        val updated = existing.copy(title = title, description = description, updatedAt = clock.now())
        writeToDb(updated)
        dispatcher.enqueue(
            Entities.NOTICE, Ops.UPDATE, id,
            json.encodeToString(UpdateNoticePayload.serializer(), UpdateNoticePayload(id, title, description))
        )
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(updated)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        db.noticeQueries.softDelete(clock.now().toEpoch(), id)
        dispatcher.enqueue(
            Entities.NOTICE, Ops.DELETE, id,
            json.encodeToString(DeleteNoticePayload.serializer(), DeleteNoticePayload(id))
        )
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(Unit)
    }

    private suspend fun pull(condominiumId: String) {
        if (!network.isOnline.value) return
        val lastCursor = db.syncMetadataQueries.getCursor(SyncCursors.noticesOf(condominiumId))
            .executeAsOneOrNull()
        val since = lastCursor?.let { Instant.fromEpochMilliseconds(it).toString() }
        runCatching { api.list(condominiumId, since) }.onSuccess { items ->
            var maxUpdated = lastCursor ?: 0L
            items.forEach { dto ->
                persist(dto)
                val u = dto.updatedAt.let { Instant.parse(it) }.toEpochMilliseconds()
                if (u > maxUpdated) maxUpdated = u
            }
            db.syncMetadataQueries.upsertCursor(SyncCursors.noticesOf(condominiumId), maxUpdated)
        }
    }

    private fun persist(dto: NoticeDto) {
        db.noticeQueries.upsert(
            id = dto.id,
            condominiumId = dto.condominiumId,
            title = dto.title,
            description = dto.description,
            authorId = dto.authorId,
            authorName = dto.authorName,
            createdAt = Instant.parse(dto.createdAt).toEpoch(),
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version,
            deleted = if (dto.deleted) 1L else 0L,
        )
    }

    private fun writeToDb(notice: Notice) {
        db.noticeQueries.upsert(
            id = notice.id,
            condominiumId = notice.condominiumId,
            title = notice.title,
            description = notice.description,
            authorId = notice.authorId,
            authorName = notice.authorName,
            createdAt = notice.createdAt.toEpoch(),
            updatedAt = (notice.updatedAt ?: notice.createdAt).toEpoch(),
            version = 0,
            deleted = 0,
        )
    }

    private fun br.tec.wrcoder.meucondominio.data.local.db.Notice_entity.toDomain(): Notice = Notice(
        id = id,
        condominiumId = condominiumId,
        title = title,
        description = description,
        authorId = authorId,
        authorName = authorName,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    )
}
