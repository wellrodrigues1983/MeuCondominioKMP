package br.tec.wrcoder.meucondominio.data.repository.remote

import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.storage.AuthTokens
import br.tec.wrcoder.meucondominio.core.storage.TokenStore
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import br.tec.wrcoder.meucondominio.data.mapper.toDomain
import br.tec.wrcoder.meucondominio.data.mapper.toEpoch
import br.tec.wrcoder.meucondominio.data.remote.AuthApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.AuthSessionDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateMemberRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.JoinCondominiumRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.LoginRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.RegisterCondominiumRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.UpdateAvatarRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.UserDto
import br.tec.wrcoder.meucondominio.domain.model.AuthSession
import br.tec.wrcoder.meucondominio.domain.model.CreateMemberInput
import br.tec.wrcoder.meucondominio.domain.model.JoinCondominiumInput
import br.tec.wrcoder.meucondominio.domain.model.LoginCredentials
import br.tec.wrcoder.meucondominio.domain.model.RegisterCondominiumInput
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant

class RemoteAuthRepository(
    private val api: AuthApiService,
    private val tokens: TokenStore,
    private val db: MeuCondominioDb,
) : AuthRepository {

    private val _session = MutableStateFlow(restoreSession())
    override val session: Flow<AuthSession?> = _session.asStateFlow()

    override suspend fun login(credentials: LoginCredentials): AppResult<AuthSession> =
        runRemote { api.login(LoginRequestDto(credentials.email, credentials.password)) }
            .onAuth()

    override suspend fun registerCondominium(input: RegisterCondominiumInput): AppResult<AuthSession> =
        runRemote {
            api.registerCondominium(
                RegisterCondominiumRequestDto(
                    input.condominiumName, input.address, input.adminName, input.adminEmail, input.adminPassword,
                )
            )
        }.onAuth()

    override suspend fun joinCondominium(input: JoinCondominiumInput): AppResult<AuthSession> =
        runRemote {
            api.joinCondominium(
                JoinCondominiumRequestDto(
                    input.condoCode, input.unitIdentifier, input.name,
                    input.email, input.password, input.phone,
                )
            )
        }.onAuth()

    override suspend fun logout() {
        runCatching { api.logout() }
        tokens.clear()
        _session.value = null
    }

    override suspend fun currentUser(): User? {
        val userId = tokens.currentUserId.value ?: return null
        db.userQueries.getUser(userId).executeAsOneOrNull()?.let { row ->
            return User(
                id = row.id, name = row.name, email = row.email, phone = row.phone,
                role = br.tec.wrcoder.meucondominio.domain.model.UserRole.valueOf(row.role),
                condominiumId = row.condominiumId, unitId = row.unitId, avatarUrl = row.avatarUrl,
                createdAt = Instant.fromEpochMilliseconds(row.createdAt),
            )
        }
        return runCatching { api.me() }.getOrNull()?.also(::persistUser)?.toDomain()
    }

    override suspend fun createUnitMember(input: CreateMemberInput): AppResult<User> =
        runRemote {
            api.createUnitMember(
                input.unitId,
                CreateMemberRequestDto(input.name, input.email, input.password, input.phone),
            ).also(::persistUser).toDomain()
        }

    override suspend fun listUnitMembers(unitId: String): AppResult<List<User>> =
        runRemote {
            api.listUnitMembers(unitId).also { list -> list.forEach(::persistUser) }
                .map { it.toDomain() }
        }

    override suspend fun updateAvatar(userId: String, avatarUrl: String?): AppResult<User> =
        runRemote {
            api.updateAvatar(UpdateAvatarRequestDto(avatarUrl))
                .also(::persistUser)
                .toDomain()
        }.also { result ->
            if (result is AppResult.Success) {
                val current = _session.value
                if (current != null && current.user.id == userId) {
                    _session.value = current.copy(user = result.data)
                }
            }
        }

    private fun AppResult<AuthSessionDto>.onAuth(): AppResult<AuthSession> = when (this) {
        is AppResult.Success -> {
            tokens.save(AuthTokens(data.accessToken, data.refreshToken), data.user.id)
            persistUser(data.user)
            val user = data.user.toDomain()
            val sess = AuthSession(token = data.accessToken, user = user, issuedAt = user.createdAt)
            _session.value = sess
            AppResult.Success(sess)
        }
        is AppResult.Failure -> this
    }

    private fun restoreSession(): AuthSession? {
        val t = tokens.read() ?: return null
        val userId = tokens.currentUserId.value ?: return null
        val row = db.userQueries.getUser(userId).executeAsOneOrNull() ?: return null
        val user = User(
            id = row.id, name = row.name, email = row.email, phone = row.phone,
            role = br.tec.wrcoder.meucondominio.domain.model.UserRole.valueOf(row.role),
            condominiumId = row.condominiumId, unitId = row.unitId, avatarUrl = row.avatarUrl,
            createdAt = Instant.fromEpochMilliseconds(row.createdAt),
        )
        return AuthSession(token = t.accessToken, user = user, issuedAt = user.createdAt)
    }

    private fun persistUser(dto: UserDto) {
        db.userQueries.upsertUser(
            id = dto.id, name = dto.name, email = dto.email, phone = dto.phone,
            role = dto.role, condominiumId = dto.condominiumId, unitId = dto.unitId,
            avatarUrl = dto.avatarUrl,
            createdAt = Instant.parse(dto.createdAt).toEpoch(),
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
        )
    }
}
