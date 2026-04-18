package br.tec.wrcoder.meucondominio.data.repository.remote

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import br.tec.wrcoder.meucondominio.data.mapper.decodeStrings
import br.tec.wrcoder.meucondominio.data.mapper.encodeStrings
import br.tec.wrcoder.meucondominio.data.mapper.toEpoch
import br.tec.wrcoder.meucondominio.data.remote.ListingsApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateListingRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ListingDto
import br.tec.wrcoder.meucondominio.data.sync.Entities
import br.tec.wrcoder.meucondominio.data.sync.Ops
import br.tec.wrcoder.meucondominio.data.sync.OutboxDispatcher
import br.tec.wrcoder.meucondominio.data.sync.SyncCursors
import br.tec.wrcoder.meucondominio.domain.model.Listing
import br.tec.wrcoder.meucondominio.domain.model.ListingStatus
import br.tec.wrcoder.meucondominio.domain.repository.ListingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CreateListingPayload(
    val id: String, val condominiumId: String, val title: String, val description: String,
    val price: Double?, val imageUrls: List<String>,
)

@Serializable
private data class CloseListingPayload(val id: String)

@Serializable
private data class RenewListingPayload(val id: String)

class RemoteListingsRepository(
    private val db: MeuCondominioDb,
    private val api: ListingsApiService,
    private val dispatcher: OutboxDispatcher,
    private val network: NetworkMonitor,
    private val clock: AppClock,
    private val json: Json,
) : ListingRepository {

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        dispatcher.register(Entities.LISTING, Ops.CREATE) { payload, _ ->
            val p = json.decodeFromString(CreateListingPayload.serializer(), payload)
            persist(api.create(p.condominiumId,
                CreateListingRequestDto(p.title, p.description, p.price, p.imageUrls)))
        }
        dispatcher.register(Entities.LISTING, Ops.CLOSE) { payload, _ ->
            val p = json.decodeFromString(CloseListingPayload.serializer(), payload)
            persist(api.close(p.id))
        }
        dispatcher.register(Entities.LISTING, Ops.RENEW) { payload, _ ->
            val p = json.decodeFromString(RenewListingPayload.serializer(), payload)
            persist(api.renew(p.id))
        }
    }

    override fun observe(condominiumId: String): Flow<List<Listing>> =
        db.listingQueries.observe(condominiumId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
            .onStart { bgScope.launch { pull(condominiumId) } }

    override suspend fun create(
        condominiumId: String, authorUserId: String, unitIdentifier: String,
        title: String, description: String, price: Double?, imageUrls: List<String>,
    ): AppResult<Listing> {
        val id = newId()
        val now = clock.now()
        val expiresAt = now.plus(30, DateTimeUnit.DAY, TimeZone.UTC)
        val author = db.userQueries.getUser(authorUserId).executeAsOneOrNull()
        db.listingQueries.upsert(
            id = id, condominiumId = condominiumId, authorUserId = authorUserId,
            authorName = author?.name ?: "",
            unitIdentifier = unitIdentifier, title = title, description = description,
            price = price, imageUrlsJson = encodeStrings(imageUrls), status = "ACTIVE",
            createdAt = now.toEpoch(), expiresAt = expiresAt.toEpoch(), updatedAt = now.toEpoch(),
            version = 0, deleted = 0,
        )
        dispatcher.enqueue(Entities.LISTING, Ops.CREATE, id,
            json.encodeToString(CreateListingPayload.serializer(),
                CreateListingPayload(id, condominiumId, title, description, price, imageUrls)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(db.listingQueries.get(id).executeAsOne().toDomain())
    }

    override suspend fun close(id: String, userId: String): AppResult<Listing> =
        changeStatus(id, "CLOSED", Ops.CLOSE,
            json.encodeToString(CloseListingPayload.serializer(), CloseListingPayload(id)))

    override suspend fun renew(id: String, userId: String): AppResult<Listing> {
        val row = db.listingQueries.get(id).executeAsOneOrNull()
            ?: return AppResult.Failure(AppError.NotFound("Anúncio não encontrado"))
        val now = clock.now()
        val newExpiry = now.plus(30, DateTimeUnit.DAY, TimeZone.UTC).toEpoch()
        db.listingQueries.upsert(
            id = row.id, condominiumId = row.condominiumId, authorUserId = row.authorUserId,
            authorName = row.authorName, unitIdentifier = row.unitIdentifier,
            title = row.title, description = row.description, price = row.price,
            imageUrlsJson = row.imageUrlsJson, status = "ACTIVE",
            createdAt = row.createdAt, expiresAt = newExpiry, updatedAt = now.toEpoch(),
            version = row.version, deleted = 0,
        )
        dispatcher.enqueue(Entities.LISTING, Ops.RENEW, id,
            json.encodeToString(RenewListingPayload.serializer(), RenewListingPayload(id)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(db.listingQueries.get(id).executeAsOne().toDomain())
    }

    private suspend fun changeStatus(id: String, status: String, op: String, payload: String): AppResult<Listing> {
        val row = db.listingQueries.get(id).executeAsOneOrNull()
            ?: return AppResult.Failure(AppError.NotFound("Anúncio não encontrado"))
        val now = clock.now().toEpoch()
        db.listingQueries.upsert(
            id = row.id, condominiumId = row.condominiumId, authorUserId = row.authorUserId,
            authorName = row.authorName, unitIdentifier = row.unitIdentifier,
            title = row.title, description = row.description, price = row.price,
            imageUrlsJson = row.imageUrlsJson, status = status,
            createdAt = row.createdAt, expiresAt = row.expiresAt, updatedAt = now,
            version = row.version, deleted = 0,
        )
        dispatcher.enqueue(Entities.LISTING, op, id, payload)
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(db.listingQueries.get(id).executeAsOne().toDomain())
    }

    private suspend fun pull(condominiumId: String) {
        if (!network.isOnline.value) return
        val since = db.syncMetadataQueries.getCursor(SyncCursors.listingsOf(condominiumId))
            .executeAsOneOrNull()?.let { Instant.fromEpochMilliseconds(it).toString() }
        runCatching { api.list(condominiumId, since) }.onSuccess { items ->
            items.forEach(::persist)
            items.maxOfOrNull { Instant.parse(it.updatedAt).toEpoch() }?.let {
                db.syncMetadataQueries.upsertCursor(SyncCursors.listingsOf(condominiumId), it)
            }
        }
    }

    private fun persist(dto: ListingDto) {
        db.listingQueries.upsert(
            id = dto.id, condominiumId = dto.condominiumId, authorUserId = dto.authorUserId,
            authorName = dto.authorName, unitIdentifier = dto.unitIdentifier,
            title = dto.title, description = dto.description, price = dto.price,
            imageUrlsJson = encodeStrings(dto.imageUrls), status = dto.status,
            createdAt = Instant.parse(dto.createdAt).toEpoch(),
            expiresAt = Instant.parse(dto.expiresAt).toEpoch(),
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
        )
    }

    private fun br.tec.wrcoder.meucondominio.data.local.db.Listing_entity.toDomain(): Listing = Listing(
        id = id, condominiumId = condominiumId, authorUserId = authorUserId, authorName = authorName,
        unitIdentifier = unitIdentifier, title = title, description = description, price = price,
        imageUrls = decodeStrings(imageUrlsJson),
        status = ListingStatus.valueOf(status),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        expiresAt = Instant.fromEpochMilliseconds(expiresAt),
    )
}
