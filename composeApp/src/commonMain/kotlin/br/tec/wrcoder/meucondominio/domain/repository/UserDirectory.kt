package br.tec.wrcoder.meucondominio.domain.repository

import br.tec.wrcoder.meucondominio.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserDirectory {
    fun observeUsers(condominiumId: String): Flow<List<User>>
    suspend fun refresh(condominiumId: String)
}
