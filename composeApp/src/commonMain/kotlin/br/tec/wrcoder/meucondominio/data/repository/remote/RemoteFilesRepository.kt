package br.tec.wrcoder.meucondominio.data.repository.remote

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import br.tec.wrcoder.meucondominio.data.mapper.toEpoch
import br.tec.wrcoder.meucondominio.data.remote.FilesApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.FileDocDto
import br.tec.wrcoder.meucondominio.data.sync.SyncCursors
import br.tec.wrcoder.meucondominio.domain.model.FileDoc
import br.tec.wrcoder.meucondominio.domain.repository.FilesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class RemoteFilesRepository(
    private val db: MeuCondominioDb,
    private val api: FilesApiService,
    private val network: NetworkMonitor,
    private val clock: AppClock,
) : FilesRepository {

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun observe(condominiumId: String): Flow<List<FileDoc>> =
        db.fileDocQueries.observe(condominiumId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
            .onStart { bgScope.launch { pull(condominiumId) } }

    override suspend fun upload(
        condominiumId: String, uploadedByUserId: String, title: String,
        description: String?, fileName: String, sizeBytes: Long, bytes: ByteArray,
    ): AppResult<FileDoc> {
        if (!network.isOnline.value) {
            return AppResult.Failure(AppError.Network("Upload indisponível offline. Conecte à internet e tente novamente."))
        }
        return runRemote {
            val dto = api.upload(condominiumId, title, description, fileName, bytes)
            persist(dto)
            dto.toDomainDoc()
        }
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        db.fileDocQueries.upsert(
            id = id, condominiumId = "", title = "", description = null, fileUrl = "",
            sizeBytes = 0, uploadedByUserId = "", uploadedAt = 0,
            updatedAt = clock.now().toEpoch(), version = 0, deleted = 1,
        )
        if (network.isOnline.value) {
            return runRemote { api.delete(id) }
        }
        return AppResult.Success(Unit)
    }

    private suspend fun pull(condominiumId: String) {
        if (!network.isOnline.value) return
        val since = db.syncMetadataQueries.getCursor(SyncCursors.filesOf(condominiumId))
            .executeAsOneOrNull()?.let { Instant.fromEpochMilliseconds(it).toString() }
        runCatching { api.list(condominiumId, since) }.onSuccess { items ->
            items.forEach(::persist)
            items.maxOfOrNull { Instant.parse(it.updatedAt).toEpoch() }?.let {
                db.syncMetadataQueries.upsertCursor(SyncCursors.filesOf(condominiumId), it)
            }
        }
    }

    private fun persist(dto: FileDocDto) {
        db.fileDocQueries.upsert(
            id = dto.id, condominiumId = dto.condominiumId, title = dto.title,
            description = dto.description, fileUrl = dto.fileUrl, sizeBytes = dto.sizeBytes,
            uploadedByUserId = dto.uploadedByUserId,
            uploadedAt = Instant.parse(dto.uploadedAt).toEpoch(),
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
        )
    }

    private fun FileDocDto.toDomainDoc(): FileDoc = FileDoc(
        id = id, condominiumId = condominiumId, title = title, description = description,
        fileUrl = fileUrl, sizeBytes = sizeBytes, uploadedByUserId = uploadedByUserId,
        uploadedAt = Instant.parse(uploadedAt),
    )

    private fun br.tec.wrcoder.meucondominio.data.local.db.File_entity.toDomain(): FileDoc = FileDoc(
        id = id, condominiumId = condominiumId, title = title, description = description,
        fileUrl = fileUrl, sizeBytes = sizeBytes, uploadedByUserId = uploadedByUserId,
        uploadedAt = Instant.fromEpochMilliseconds(uploadedAt),
    )
}
