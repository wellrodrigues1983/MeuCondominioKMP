package br.tec.wrcoder.meucondominio.data.repository.remote

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import br.tec.wrcoder.meucondominio.data.mapper.toEpoch
import br.tec.wrcoder.meucondominio.data.remote.CondominiumApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.UserDto
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import br.tec.wrcoder.meucondominio.domain.repository.UserDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class RemoteUserDirectory(
    private val db: MeuCondominioDb,
    private val api: CondominiumApiService,
    private val network: NetworkMonitor,
) : UserDirectory {

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshed = mutableSetOf<String>()

    override fun observeUsers(condominiumId: String): Flow<List<User>> =
        db.userQueries.listUsersByCondominium(condominiumId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { r ->
                    User(
                        id = r.id, name = r.name, email = r.email, phone = r.phone,
                        role = UserRole.valueOf(r.role), condominiumId = r.condominiumId,
                        unitId = r.unitId, avatarUrl = r.avatarUrl,
                        createdAt = Instant.fromEpochMilliseconds(r.createdAt),
                    )
                }
            }
            .onStart {
                if (refreshed.add(condominiumId)) {
                    bgScope.launch { refresh(condominiumId) }
                }
            }

    override suspend fun refresh(condominiumId: String) {
        if (!network.isOnline.value) return
        runCatching { api.listMembers(condominiumId) }
            .onFailure { println("[UserDir] listMembers failed: ${it.message}") }
            .onSuccess { list ->
                println("[UserDir] listMembers($condominiumId) returned ${list.size}")
                list.forEach(::persistUser)
                refreshed += condominiumId
            }
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
