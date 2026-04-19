package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.repository.UserDirectory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FakeUserDirectory(private val store: InMemoryStore) : UserDirectory {
    override fun observeUsers(condominiumId: String): Flow<List<User>> =
        store.users.map { all -> all.filter { it.condominiumId == condominiumId } }

    override suspend fun refresh(condominiumId: String) { /* no-op */ }
}
