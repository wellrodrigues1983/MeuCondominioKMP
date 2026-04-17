package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.core.asSuccess
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.domain.model.Notice
import br.tec.wrcoder.meucondominio.domain.repository.NoticeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FakeNoticeRepository(
    private val store: InMemoryStore,
    private val clock: AppClock,
) : NoticeRepository {

    override fun observe(condominiumId: String): Flow<List<Notice>> =
        store.notices.map { all -> all.filter { it.condominiumId == condominiumId }.sortedByDescending { it.createdAt } }

    override suspend fun create(
        condominiumId: String,
        authorId: String,
        authorName: String,
        title: String,
        description: String,
    ): AppResult<Notice> {
        if (title.isBlank() || description.isBlank()) {
            return AppError.Validation("Informe título e descrição").asFailure()
        }
        val notice = Notice(
            id = newId(),
            condominiumId = condominiumId,
            title = title.trim(),
            description = description.trim(),
            authorId = authorId,
            authorName = authorName,
            createdAt = clock.now(),
        )
        store.notices.value = store.notices.value + notice
        return notice.asSuccess()
    }

    override suspend fun update(id: String, title: String, description: String): AppResult<Notice> {
        val current = store.notices.value.firstOrNull { it.id == id }
            ?: return AppError.NotFound("Aviso não encontrado").asFailure()
        val updated = current.copy(title = title.trim(), description = description.trim(), updatedAt = clock.now())
        store.notices.value = store.notices.value.map { if (it.id == id) updated else it }
        return updated.asSuccess()
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        store.notices.value = store.notices.value.filterNot { it.id == id }
        return Unit.asSuccess()
    }
}
