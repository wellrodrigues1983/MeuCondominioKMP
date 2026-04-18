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
import br.tec.wrcoder.meucondominio.data.remote.SpacesApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.CancelReservationRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CommonSpaceDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateReservationRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateSpaceRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ReservationDto
import br.tec.wrcoder.meucondominio.data.remote.dto.UpdateSpaceRequestDto
import br.tec.wrcoder.meucondominio.data.sync.Entities
import br.tec.wrcoder.meucondominio.data.sync.Ops
import br.tec.wrcoder.meucondominio.data.sync.OutboxDispatcher
import br.tec.wrcoder.meucondominio.data.sync.SyncCursors
import br.tec.wrcoder.meucondominio.domain.model.CommonSpace
import br.tec.wrcoder.meucondominio.domain.model.Reservation
import br.tec.wrcoder.meucondominio.domain.model.ReservationStatus
import br.tec.wrcoder.meucondominio.domain.repository.SpaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CreateSpacePayload(
    val id: String, val condominiumId: String, val name: String, val description: String,
    val price: Double, val imageUrls: List<String>,
)

@Serializable
private data class UpdateSpacePayload(
    val id: String, val name: String, val description: String, val price: Double,
    val imageUrls: List<String>, val active: Boolean,
)

@Serializable
private data class DeleteSpacePayload(val id: String)

@Serializable
private data class ReservePayload(val id: String, val spaceId: String, val unitId: String, val date: String)

@Serializable
private data class CancelPayload(val id: String, val reason: String?, val byStaff: Boolean)

class RemoteSpacesRepository(
    private val db: MeuCondominioDb,
    private val api: SpacesApiService,
    private val dispatcher: OutboxDispatcher,
    private val network: NetworkMonitor,
    private val clock: AppClock,
    private val json: Json,
) : SpaceRepository {

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        dispatcher.register(Entities.SPACE, Ops.CREATE) { payload, _ ->
            val p = json.decodeFromString(CreateSpacePayload.serializer(), payload)
            persistSpace(api.createSpace(p.condominiumId,
                CreateSpaceRequestDto(p.name, p.description, p.price, p.imageUrls)))
        }
        dispatcher.register(Entities.SPACE, Ops.UPDATE) { payload, _ ->
            val p = json.decodeFromString(UpdateSpacePayload.serializer(), payload)
            persistSpace(api.updateSpace(p.id,
                UpdateSpaceRequestDto(p.name, p.description, p.price, p.imageUrls, p.active)))
        }
        dispatcher.register(Entities.SPACE, Ops.DELETE) { payload, _ ->
            val p = json.decodeFromString(DeleteSpacePayload.serializer(), payload)
            api.deleteSpace(p.id)
        }
        dispatcher.register(Entities.RESERVATION, Ops.CREATE) { payload, _ ->
            val p = json.decodeFromString(ReservePayload.serializer(), payload)
            persistReservation(api.reserve(p.spaceId, CreateReservationRequestDto(p.unitId, p.date)))
        }
        dispatcher.register(Entities.RESERVATION, Ops.CANCEL_RESIDENT) { payload, _ ->
            val p = json.decodeFromString(CancelPayload.serializer(), payload)
            persistReservation(api.cancelByResident(p.id))
        }
        dispatcher.register(Entities.RESERVATION, Ops.CANCEL_STAFF) { payload, _ ->
            val p = json.decodeFromString(CancelPayload.serializer(), payload)
            persistReservation(api.cancelByStaff(p.id, CancelReservationRequestDto(p.reason)))
        }
    }

    override fun observeSpaces(condominiumId: String): Flow<List<CommonSpace>> =
        db.spaceQueries.observeSpaces(condominiumId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
            .onStart { bgScope.launch { pullSpaces(condominiumId) } }

    override suspend fun createSpace(
        condominiumId: String, name: String, description: String,
        price: Double, imageUrls: List<String>,
    ): AppResult<CommonSpace> {
        val id = newId()
        val now = clock.now().toEpoch()
        db.spaceQueries.upsertSpace(id, condominiumId, name, description, price,
            encodeStrings(imageUrls), 1L, now, 0, 0)
        dispatcher.enqueue(Entities.SPACE, Ops.CREATE, id,
            json.encodeToString(CreateSpacePayload.serializer(),
                CreateSpacePayload(id, condominiumId, name, description, price, imageUrls)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(CommonSpace(id, condominiumId, name, description, price, imageUrls, true))
    }

    override suspend fun updateSpace(space: CommonSpace): AppResult<CommonSpace> {
        val now = clock.now().toEpoch()
        db.spaceQueries.upsertSpace(space.id, space.condominiumId, space.name, space.description,
            space.price, encodeStrings(space.imageUrls), if (space.active) 1L else 0L, now, 0, 0)
        dispatcher.enqueue(Entities.SPACE, Ops.UPDATE, space.id,
            json.encodeToString(UpdateSpacePayload.serializer(),
                UpdateSpacePayload(space.id, space.name, space.description, space.price,
                    space.imageUrls, space.active)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(space)
    }

    override suspend fun deleteSpace(id: String): AppResult<Unit> {
        db.spaceQueries.softDeleteSpace(clock.now().toEpoch(), id)
        dispatcher.enqueue(Entities.SPACE, Ops.DELETE, id,
            json.encodeToString(DeleteSpacePayload.serializer(), DeleteSpacePayload(id)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(Unit)
    }

    override fun observeReservations(spaceId: String): Flow<List<Reservation>> =
        db.spaceQueries.observeReservationsBySpace(spaceId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
            .onStart { bgScope.launch { pullReservationsBySpace(spaceId) } }

    override fun observeReservationsByUnit(unitId: String): Flow<List<Reservation>> =
        db.spaceQueries.observeReservationsByUnit(unitId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
            .onStart { bgScope.launch { pullReservationsByUnit(unitId) } }

    override suspend fun reserve(
        spaceId: String, unitId: String, residentUserId: String, date: LocalDate,
    ): AppResult<Reservation> {
        val id = newId()
        val now = clock.now()
        val space = db.spaceQueries.getSpace(spaceId).executeAsOneOrNull()
            ?: return AppResult.Failure(AppError.NotFound("Espaço não encontrado"))
        val unit = db.condominiumQueries.getUnit(unitId).executeAsOneOrNull()
            ?: return AppResult.Failure(AppError.NotFound("Unidade não encontrada"))
        val user = db.userQueries.getUser(residentUserId).executeAsOneOrNull()
            ?: return AppResult.Failure(AppError.NotFound("Usuário não encontrado"))
        db.spaceQueries.upsertReservation(
            id = id, spaceId = spaceId, spaceName = space.name, unitId = unitId,
            unitIdentifier = unit.identifier, residentUserId = residentUserId,
            residentName = user.name, dateEpochDay = date.toEpochDays().toLong(),
            status = "CONFIRMED", createdAt = now.toEpoch(), cancelledAt = null,
            cancellationReason = null, updatedAt = now.toEpoch(), version = 0, deleted = 0,
        )
        dispatcher.enqueue(Entities.RESERVATION, Ops.CREATE, id,
            json.encodeToString(ReservePayload.serializer(),
                ReservePayload(id, spaceId, unitId, date.toString())))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(
            Reservation(id, spaceId, space.name, unitId, unit.identifier,
                residentUserId, user.name, date, ReservationStatus.CONFIRMED, now)
        )
    }

    override suspend fun cancelByResident(
        reservationId: String, residentUserId: String,
    ): AppResult<Reservation> = cancel(reservationId, null, byStaff = false)

    override suspend fun cancelByStaff(
        reservationId: String, staffUserId: String, reason: String,
    ): AppResult<Reservation> = cancel(reservationId, reason, byStaff = true)

    private suspend fun cancel(id: String, reason: String?, byStaff: Boolean): AppResult<Reservation> {
        val now = clock.now()
        val row = db.spaceQueries.getReservation(id).executeAsOneOrNull()
            ?: return AppResult.Failure(AppError.NotFound("Reserva não encontrada"))
        val newStatus = if (byStaff) "CANCELLED_BY_STAFF" else "CANCELLED_BY_RESIDENT"
        db.spaceQueries.upsertReservation(
            id = row.id, spaceId = row.spaceId, spaceName = row.spaceName, unitId = row.unitId,
            unitIdentifier = row.unitIdentifier, residentUserId = row.residentUserId,
            residentName = row.residentName, dateEpochDay = row.dateEpochDay,
            status = newStatus, createdAt = row.createdAt, cancelledAt = now.toEpoch(),
            cancellationReason = reason, updatedAt = now.toEpoch(), version = row.version, deleted = 0,
        )
        val op = if (byStaff) Ops.CANCEL_STAFF else Ops.CANCEL_RESIDENT
        dispatcher.enqueue(Entities.RESERVATION, op, id,
            json.encodeToString(CancelPayload.serializer(), CancelPayload(id, reason, byStaff)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(db.spaceQueries.getReservation(id).executeAsOne().toDomain())
    }

    private suspend fun pullSpaces(condominiumId: String) {
        if (!network.isOnline.value) return
        val since = db.syncMetadataQueries.getCursor(SyncCursors.spacesOf(condominiumId))
            .executeAsOneOrNull()?.let { Instant.fromEpochMilliseconds(it).toString() }
        runCatching { api.listSpaces(condominiumId, since) }.onSuccess { items ->
            items.forEach(::persistSpace)
            items.maxOfOrNull { Instant.parse(it.updatedAt).toEpoch() }?.let {
                db.syncMetadataQueries.upsertCursor(SyncCursors.spacesOf(condominiumId), it)
            }
        }
    }

    private suspend fun pullReservationsBySpace(spaceId: String) {
        if (!network.isOnline.value) return
        val key = SyncCursors.reservationsOfSpace(spaceId)
        val since = db.syncMetadataQueries.getCursor(key).executeAsOneOrNull()
            ?.let { Instant.fromEpochMilliseconds(it).toString() }
        runCatching { api.listReservationsBySpace(spaceId, since) }.onSuccess { items ->
            items.forEach(::persistReservation)
            items.maxOfOrNull { Instant.parse(it.updatedAt).toEpoch() }?.let {
                db.syncMetadataQueries.upsertCursor(key, it)
            }
        }
    }

    private suspend fun pullReservationsByUnit(unitId: String) {
        if (!network.isOnline.value) return
        val key = SyncCursors.reservationsOfUnit(unitId)
        val since = db.syncMetadataQueries.getCursor(key).executeAsOneOrNull()
            ?.let { Instant.fromEpochMilliseconds(it).toString() }
        runCatching { api.listReservationsByUnit(unitId, since) }.onSuccess { items ->
            items.forEach(::persistReservation)
            items.maxOfOrNull { Instant.parse(it.updatedAt).toEpoch() }?.let {
                db.syncMetadataQueries.upsertCursor(key, it)
            }
        }
    }

    private fun persistSpace(dto: CommonSpaceDto) {
        db.spaceQueries.upsertSpace(
            id = dto.id, condominiumId = dto.condominiumId, name = dto.name, description = dto.description,
            price = dto.price, imageUrlsJson = encodeStrings(dto.imageUrls),
            active = if (dto.active) 1L else 0L, updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
        )
    }

    private fun persistReservation(dto: ReservationDto) {
        db.spaceQueries.upsertReservation(
            id = dto.id, spaceId = dto.spaceId, spaceName = dto.spaceName,
            unitId = dto.unitId, unitIdentifier = dto.unitIdentifier,
            residentUserId = dto.residentUserId, residentName = dto.residentName,
            dateEpochDay = LocalDate.parse(dto.date).toEpochDays().toLong(),
            status = dto.status,
            createdAt = Instant.parse(dto.createdAt).toEpoch(),
            cancelledAt = dto.cancelledAt?.let { Instant.parse(it).toEpoch() },
            cancellationReason = dto.cancellationReason,
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
        )
    }

    private fun br.tec.wrcoder.meucondominio.data.local.db.Space_entity.toDomain(): CommonSpace =
        CommonSpace(
            id = id, condominiumId = condominiumId, name = name, description = description,
            price = price, imageUrls = decodeStrings(imageUrlsJson), active = active == 1L,
        )

    private fun br.tec.wrcoder.meucondominio.data.local.db.Reservation_entity.toDomain(): Reservation =
        Reservation(
            id = id, spaceId = spaceId, spaceName = spaceName, unitId = unitId,
            unitIdentifier = unitIdentifier, residentUserId = residentUserId, residentName = residentName,
            date = LocalDate.fromEpochDays(dateEpochDay.toInt()),
            status = ReservationStatus.valueOf(status),
            createdAt = Instant.fromEpochMilliseconds(createdAt),
            cancelledAt = cancelledAt?.let { Instant.fromEpochMilliseconds(it) },
            cancellationReason = cancellationReason,
        )
}
