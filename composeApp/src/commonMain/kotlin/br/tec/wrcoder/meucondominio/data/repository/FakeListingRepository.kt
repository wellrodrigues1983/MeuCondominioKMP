package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.core.asSuccess
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.domain.model.Listing
import br.tec.wrcoder.meucondominio.domain.model.ListingStatus
import br.tec.wrcoder.meucondominio.domain.repository.ListingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.days

private val LISTING_DURATION = 30.days

class FakeListingRepository(
    private val store: InMemoryStore,
    private val clock: AppClock,
) : ListingRepository {

    override fun observe(condominiumId: String): Flow<List<Listing>> =
        store.listings.map { all ->
            val now = clock.now()
            all.filter { it.condominiumId == condominiumId }
                .map { listing ->
                    if (listing.status == ListingStatus.ACTIVE && now > listing.expiresAt) {
                        listing.copy(status = ListingStatus.EXPIRED)
                    } else listing
                }
                .sortedByDescending { it.createdAt }
        }

    override suspend fun create(
        condominiumId: String,
        authorUserId: String,
        unitIdentifier: String,
        title: String,
        description: String,
        price: Double?,
        imageUrls: List<String>,
    ): AppResult<Listing> {
        if (title.isBlank() || description.isBlank()) {
            return AppError.Validation("Informe título e descrição").asFailure()
        }
        val author = store.users.value.firstOrNull { it.id == authorUserId }
            ?: return AppError.NotFound("Autor não encontrado").asFailure()
        val now = clock.now()
        val listing = Listing(
            id = newId(),
            condominiumId = condominiumId,
            authorUserId = authorUserId,
            authorName = author.name,
            unitIdentifier = unitIdentifier,
            title = title.trim(),
            description = description.trim(),
            price = price,
            imageUrls = imageUrls,
            status = ListingStatus.ACTIVE,
            createdAt = now,
            expiresAt = now + LISTING_DURATION,
        )
        store.listings.value = store.listings.value + listing
        return listing.asSuccess()
    }

    override suspend fun close(id: String, userId: String): AppResult<Listing> {
        val current = store.listings.value.firstOrNull { it.id == id }
            ?: return AppError.NotFound("Anúncio não encontrado").asFailure()
        if (current.authorUserId != userId) return AppError.Forbidden().asFailure()
        val updated = current.copy(status = ListingStatus.CLOSED)
        store.listings.value = store.listings.value.map { if (it.id == id) updated else it }
        return updated.asSuccess()
    }

    override suspend fun renew(id: String, userId: String): AppResult<Listing> {
        val current = store.listings.value.firstOrNull { it.id == id }
            ?: return AppError.NotFound("Anúncio não encontrado").asFailure()
        if (current.authorUserId != userId) return AppError.Forbidden().asFailure()
        val now = clock.now()
        val updated = current.copy(
            status = ListingStatus.ACTIVE,
            expiresAt = now + LISTING_DURATION,
        )
        store.listings.value = store.listings.value.map { if (it.id == id) updated else it }
        return updated.asSuccess()
    }
}
