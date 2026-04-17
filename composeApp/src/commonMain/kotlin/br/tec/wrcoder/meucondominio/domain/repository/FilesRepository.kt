package br.tec.wrcoder.meucondominio.domain.repository

import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.FileDoc
import kotlinx.coroutines.flow.Flow

interface FilesRepository {
    fun observe(condominiumId: String): Flow<List<FileDoc>>
    suspend fun upload(
        condominiumId: String,
        uploadedByUserId: String,
        title: String,
        description: String?,
        fileName: String,
        sizeBytes: Long,
        bytes: ByteArray,
    ): AppResult<FileDoc>
    suspend fun delete(id: String): AppResult<Unit>
}
