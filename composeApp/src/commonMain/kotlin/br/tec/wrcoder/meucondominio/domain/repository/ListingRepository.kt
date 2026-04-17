package br.tec.wrcoder.meucondominio.domain.repository

import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.Listing
import kotlinx.coroutines.flow.Flow

interface ListingRepository {
    fun observe(condominiumId: String): Flow<List<Listing>>
    suspend fun create(
        condominiumId: String,
        authorUserId: String,
        unitIdentifier: String,
        title: String,
        description: String,
        price: Double?,
        imageUrls: List<String>,
    ): AppResult<Listing>
    suspend fun close(id: String, userId: String): AppResult<Listing>
    suspend fun renew(id: String, userId: String): AppResult<Listing>
}
