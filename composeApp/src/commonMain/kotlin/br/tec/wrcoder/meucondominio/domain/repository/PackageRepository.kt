package br.tec.wrcoder.meucondominio.domain.repository

import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.PackageDescription
import br.tec.wrcoder.meucondominio.domain.model.PackageItem
import kotlinx.coroutines.flow.Flow

interface PackageRepository {
    fun observeByCondominium(condominiumId: String): Flow<List<PackageItem>>
    fun observeByUnit(unitId: String): Flow<List<PackageItem>>
    suspend fun register(
        condominiumId: String,
        unitId: String,
        description: String,
        carrier: String?,
        registeredByUserId: String,
    ): AppResult<PackageItem>
    suspend fun markPickedUp(id: String): AppResult<PackageItem>

    fun observeDescriptions(condominiumId: String): Flow<List<PackageDescription>>
    suspend fun createDescription(condominiumId: String, text: String): AppResult<PackageDescription>
}
