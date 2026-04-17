package br.tec.wrcoder.meucondominio.domain.repository

import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.Notice
import kotlinx.coroutines.flow.Flow

interface NoticeRepository {
    fun observe(condominiumId: String): Flow<List<Notice>>
    suspend fun create(condominiumId: String, authorId: String, authorName: String, title: String, description: String): AppResult<Notice>
    suspend fun update(id: String, title: String, description: String): AppResult<Notice>
    suspend fun delete(id: String): AppResult<Unit>
}
