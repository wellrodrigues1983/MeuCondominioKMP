package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.core.asSuccess
import br.tec.wrcoder.meucondominio.core.newCondoCode
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.core.storage.SecureStorage
import br.tec.wrcoder.meucondominio.domain.model.AuthSession
import br.tec.wrcoder.meucondominio.domain.model.Condominium
import br.tec.wrcoder.meucondominio.domain.model.CreateMemberInput
import br.tec.wrcoder.meucondominio.domain.model.JoinCondominiumInput
import br.tec.wrcoder.meucondominio.domain.model.LoginCredentials
import br.tec.wrcoder.meucondominio.domain.model.RegisterCondominiumInput
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val SESSION_USER_ID = "session_user_id"
private const val SESSION_TOKEN = "session_token"

class FakeAuthRepository(
    private val store: InMemoryStore,
    private val clock: AppClock,
    private val storage: SecureStorage,
) : AuthRepository {

    private val _session = MutableStateFlow<AuthSession?>(null)
    override val session = _session.asStateFlow()

    init {
        storage.get(SESSION_USER_ID)?.let { uid ->
            val user = store.users.value.find { it.id == uid }
            val token = storage.get(SESSION_TOKEN)
            if (user != null && token != null) {
                _session.value = AuthSession(token, user, clock.now())
            }
        }
    }

    override suspend fun login(credentials: LoginCredentials): AppResult<AuthSession> {
        val expected = store.passwords[credentials.email.lowercase()]
            ?: return AppError.Unauthorized("E-mail ou senha inválidos").asFailure()
        if (expected != credentials.password) {
            return AppError.Unauthorized("E-mail ou senha inválidos").asFailure()
        }
        val user = store.users.value.firstOrNull { it.email.equals(credentials.email, ignoreCase = true) }
            ?: return AppError.NotFound("Usuário não encontrado").asFailure()
        val session = buildSession(user)
        persist(session)
        return session.asSuccess()
    }

    override suspend fun registerCondominium(input: RegisterCondominiumInput): AppResult<AuthSession> {
        if (store.condominiums.value.any { it.name.equals(input.condominiumName, true) }) {
            return AppError.Validation("Já existe um condomínio com este nome").asFailure()
        }
        if (store.passwords.containsKey(input.adminEmail.lowercase())) {
            return AppError.Validation("E-mail já cadastrado").asFailure()
        }
        val now = clock.now()
        val condo = Condominium(
            id = newId(),
            name = input.condominiumName,
            address = input.address,
            code = newCondoCode(),
            createdAt = now,
        )
        val admin = User(
            id = newId(),
            name = input.adminName,
            email = input.adminEmail,
            role = UserRole.ADMIN,
            condominiumId = condo.id,
            createdAt = now,
        )
        store.condominiums.value = store.condominiums.value + condo
        store.users.value = store.users.value + admin
        store.passwords[input.adminEmail.lowercase()] = input.adminPassword
        val session = buildSession(admin)
        persist(session)
        return session.asSuccess()
    }

    override suspend fun joinCondominium(input: JoinCondominiumInput): AppResult<AuthSession> {
        val condo = store.condominiums.value.firstOrNull { it.code.equals(input.condoCode, true) }
            ?: return AppError.NotFound("Código do condomínio inválido").asFailure()
        val unit = store.units.value.firstOrNull {
            it.condominiumId == condo.id && it.identifier.equals(input.unitIdentifier, true)
        } ?: return AppError.NotFound("Unidade ${input.unitIdentifier} não existe neste condomínio").asFailure()
        if (store.passwords.containsKey(input.email.lowercase())) {
            return AppError.Validation("E-mail já cadastrado").asFailure()
        }
        val now = clock.now()
        val user = User(
            id = newId(),
            name = input.name,
            email = input.email,
            phone = input.phone,
            role = UserRole.RESIDENT,
            condominiumId = condo.id,
            unitId = unit.id,
            createdAt = now,
        )
        store.users.value = store.users.value + user
        if (unit.ownerUserId == null) {
            store.units.value = store.units.value.map { if (it.id == unit.id) it.copy(ownerUserId = user.id) else it }
        }
        store.passwords[input.email.lowercase()] = input.password
        joinGroupChat(user.id, condo.id)
        val session = buildSession(user)
        persist(session)
        return session.asSuccess()
    }

    override suspend fun logout() {
        storage.remove(SESSION_USER_ID)
        storage.remove(SESSION_TOKEN)
        _session.value = null
    }

    override suspend fun currentUser(): User? = _session.value?.user

    override suspend fun createUnitMember(input: CreateMemberInput): AppResult<User> {
        val owner = _session.value?.user
            ?: return AppError.Unauthorized().asFailure()
        if (owner.role != UserRole.RESIDENT || owner.unitId != input.unitId) {
            return AppError.Forbidden().asFailure()
        }
        if (store.passwords.containsKey(input.email.lowercase())) {
            return AppError.Validation("E-mail já cadastrado").asFailure()
        }
        val member = User(
            id = newId(),
            name = input.name,
            email = input.email,
            phone = input.phone,
            role = UserRole.RESIDENT,
            condominiumId = owner.condominiumId,
            unitId = input.unitId,
            createdAt = clock.now(),
        )
        store.users.value = store.users.value + member
        store.passwords[input.email.lowercase()] = input.password
        joinGroupChat(member.id, member.condominiumId)
        return member.asSuccess()
    }

    private fun joinGroupChat(userId: String, condoId: String) {
        val groupId = "group-$condoId"
        store.chatThreads.value = store.chatThreads.value.map {
            if (it.id == groupId && userId !in it.participantUserIds) {
                it.copy(participantUserIds = it.participantUserIds + userId)
            } else it
        }
    }

    override suspend fun listUnitMembers(unitId: String): AppResult<List<User>> =
        store.users.value.filter { it.unitId == unitId }.asSuccess()

    override suspend fun updateAvatar(userId: String, avatarUrl: String?): AppResult<User> =
        applyAvatar(userId, avatarUrl)

    override suspend fun uploadAvatar(
        userId: String, bytes: ByteArray, fileName: String, mime: String,
    ): AppResult<User> = applyAvatar(userId, "/assets/${newId()}")

    private fun applyAvatar(userId: String, avatarUrl: String?): AppResult<User> {
        val updated = store.users.value.firstOrNull { it.id == userId }?.copy(avatarUrl = avatarUrl)
            ?: return AppError.NotFound("Usuário não encontrado").asFailure()
        store.users.value = store.users.value.map { if (it.id == userId) updated else it }
        _session.value?.let { current ->
            if (current.user.id == userId) {
                _session.value = current.copy(user = updated)
            }
        }
        return updated.asSuccess()
    }

    private fun buildSession(user: User): AuthSession {
        val session = AuthSession(token = "fake-token-${user.id}", user = user, issuedAt = clock.now())
        _session.value = session
        return session
    }

    private fun persist(session: AuthSession) {
        storage.put(SESSION_USER_ID, session.user.id)
        storage.put(SESSION_TOKEN, session.token)
    }
}
