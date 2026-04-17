package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.core.asSuccess
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.domain.model.PackageDescription
import br.tec.wrcoder.meucondominio.domain.model.PackageItem
import br.tec.wrcoder.meucondominio.domain.model.PackageStatus
import br.tec.wrcoder.meucondominio.domain.repository.PackageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FakePackageRepository(
    private val store: InMemoryStore,
    private val clock: AppClock,
) : PackageRepository {

    override fun observeByCondominium(condominiumId: String): Flow<List<PackageItem>> =
        store.packages.map { all ->
            all.filter { it.condominiumId == condominiumId }.sortedByDescending { it.receivedAt }
        }

    override fun observeByUnit(unitId: String): Flow<List<PackageItem>> =
        store.packages.map { all ->
            all.filter { it.unitId == unitId }.sortedByDescending { it.receivedAt }
        }

    override suspend fun register(
        condominiumId: String,
        unitId: String,
        description: String,
        carrier: String?,
        registeredByUserId: String,
    ): AppResult<PackageItem> {
        val unit = store.units.value.firstOrNull { it.id == unitId }
            ?: return AppError.NotFound("Unidade não encontrada").asFailure()
        val item = PackageItem(
            id = newId(),
            condominiumId = condominiumId,
            unitId = unitId,
            unitIdentifier = unit.identifier,
            description = description.trim(),
            carrier = carrier?.trim()?.takeIf { it.isNotEmpty() },
            status = PackageStatus.RECEIVED,
            receivedAt = clock.now(),
            registeredByUserId = registeredByUserId,
        )
        store.packages.value = store.packages.value + item
        return item.asSuccess()
    }

    override suspend fun markPickedUp(id: String): AppResult<PackageItem> {
        val current = store.packages.value.firstOrNull { it.id == id }
            ?: return AppError.NotFound("Encomenda não encontrada").asFailure()
        val updated = current.copy(status = PackageStatus.PICKED_UP, pickedUpAt = clock.now())
        store.packages.value = store.packages.value.map { if (it.id == id) updated else it }
        return updated.asSuccess()
    }

    override fun observeDescriptions(condominiumId: String): Flow<List<PackageDescription>> =
        store.packageDescriptions.map { all ->
            all.filter { it.condominiumId == condominiumId }.sortedBy { it.text.lowercase() }
        }

    override suspend fun createDescription(condominiumId: String, text: String): AppResult<PackageDescription> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return AppError.Validation("Informe a descrição").asFailure()
        val existing = store.packageDescriptions.value.firstOrNull {
            it.condominiumId == condominiumId && it.text.equals(trimmed, ignoreCase = true)
        }
        if (existing != null) return existing.asSuccess()
        val desc = PackageDescription(
            id = newId(),
            condominiumId = condominiumId,
            text = trimmed,
        )
        store.packageDescriptions.value = store.packageDescriptions.value + desc
        return desc.asSuccess()
    }
}
