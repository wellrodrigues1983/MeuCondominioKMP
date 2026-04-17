package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.core.asSuccess
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.domain.model.FileDoc
import br.tec.wrcoder.meucondominio.domain.repository.FilesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FakeFilesRepository(
    private val store: InMemoryStore,
    private val clock: AppClock,
) : FilesRepository {

    override fun observe(condominiumId: String): Flow<List<FileDoc>> =
        store.files.map { all -> all.filter { it.condominiumId == condominiumId }.sortedByDescending { it.uploadedAt } }

    override suspend fun upload(
        condominiumId: String,
        uploadedByUserId: String,
        title: String,
        description: String?,
        fileName: String,
        sizeBytes: Long,
        bytes: ByteArray,
    ): AppResult<FileDoc> {
        if (!fileName.endsWith(".pdf", ignoreCase = true)) {
            return AppError.Validation("Apenas arquivos PDF são aceitos").asFailure()
        }
        if (title.isBlank()) return AppError.Validation("Informe um título").asFailure()
        val doc = FileDoc(
            id = newId(),
            condominiumId = condominiumId,
            title = title.trim(),
            description = description?.trim(),
            fileUrl = "memory://files/${fileName}",
            sizeBytes = sizeBytes,
            uploadedByUserId = uploadedByUserId,
            uploadedAt = clock.now(),
        )
        store.files.value = store.files.value + doc
        return doc.asSuccess().also { @Suppress("UNUSED_EXPRESSION") bytes }
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        store.files.value = store.files.value.filterNot { it.id == id }
        return Unit.asSuccess()
    }
}
