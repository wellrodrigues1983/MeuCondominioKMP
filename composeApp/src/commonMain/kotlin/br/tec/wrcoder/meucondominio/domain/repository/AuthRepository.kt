package br.tec.wrcoder.meucondominio.domain.repository

import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.AuthSession
import br.tec.wrcoder.meucondominio.domain.model.CreateMemberInput
import br.tec.wrcoder.meucondominio.domain.model.JoinCondominiumInput
import br.tec.wrcoder.meucondominio.domain.model.LoginCredentials
import br.tec.wrcoder.meucondominio.domain.model.RegisterCondominiumInput
import br.tec.wrcoder.meucondominio.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val session: Flow<AuthSession?>
    suspend fun login(credentials: LoginCredentials): AppResult<AuthSession>
    suspend fun registerCondominium(input: RegisterCondominiumInput): AppResult<AuthSession>
    suspend fun joinCondominium(input: JoinCondominiumInput): AppResult<AuthSession>
    suspend fun logout()
    suspend fun currentUser(): User?
    suspend fun createUnitMember(input: CreateMemberInput): AppResult<User>
    suspend fun listUnitMembers(unitId: String): AppResult<List<User>>
    suspend fun updateAvatar(userId: String, avatarUrl: String?): AppResult<User>
}
