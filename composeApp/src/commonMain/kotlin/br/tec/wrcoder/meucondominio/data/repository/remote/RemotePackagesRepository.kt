package br.tec.wrcoder.meucondominio.data.repository.remote

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import br.tec.wrcoder.meucondominio.data.mapper.toEpoch
import br.tec.wrcoder.meucondominio.data.remote.PackagesApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.CreatePackageDescriptionRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PackageDescriptionDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PackageItemDto
import br.tec.wrcoder.meucondominio.data.remote.dto.RegisterPackageRequestDto
import br.tec.wrcoder.meucondominio.data.sync.Entities
import br.tec.wrcoder.meucondominio.data.sync.Ops
import br.tec.wrcoder.meucondominio.data.sync.OutboxDispatcher
import br.tec.wrcoder.meucondominio.data.sync.SyncCursors
import br.tec.wrcoder.meucondominio.domain.model.PackageDescription
import br.tec.wrcoder.meucondominio.domain.model.PackageItem
import br.tec.wrcoder.meucondominio.domain.model.PackageStatus
import br.tec.wrcoder.meucondominio.domain.repository.PackageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RegisterPackagePayload(
    val id: String, val condominiumId: String, val unitId: String,
    val description: String, val carrier: String?,
)

@Serializable
private data class PickupPayload(val id: String)

@Serializable
private data class CreateDescriptionPayload(val id: String, val condominiumId: String, val text: String)

class RemotePackagesRepository(
    private val db: MeuCondominioDb,
    private val api: PackagesApiService,
    private val dispatcher: OutboxDispatcher,
    private val network: NetworkMonitor,
    private val clock: AppClock,
    private val json: Json,
) : PackageRepository {

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reconciledPackages = mutableSetOf<String>()
    private val reconciledPackagesByUnit = mutableSetOf<String>()
    private val reconciledDescriptions = mutableSetOf<String>()

    init {
        dispatcher.register(Entities.PACKAGE, Ops.CREATE) { payload, _ ->
            val p = json.decodeFromString(RegisterPackagePayload.serializer(), payload)
            val dto = api.register(p.condominiumId, RegisterPackageRequestDto(p.unitId, p.description, p.carrier))
            if (dto.id != p.id) db.packageQueries.deletePackageById(p.id)
            persistPackage(dto)
        }
        dispatcher.register(Entities.PACKAGE, Ops.PICKUP) { payload, _ ->
            val p = json.decodeFromString(PickupPayload.serializer(), payload)
            persistPackage(api.markPickedUp(p.id))
        }
        dispatcher.register(Entities.PACKAGE_DESCRIPTION, Ops.CREATE) { payload, _ ->
            val p = json.decodeFromString(CreateDescriptionPayload.serializer(), payload)
            val dto =
                api.createDescription(p.condominiumId, CreatePackageDescriptionRequestDto(p.text))
            if (dto.id != p.id) db.packageQueries.deleteDescriptionById(p.id)
            persistDescription(dto)
        }
    }

    override fun observeByCondominium(condominiumId: String): Flow<List<PackageItem>> =
        db.packageQueries.observeByCondominium(condominiumId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
            .onStart { bgScope.launch { pullPackages(condominiumId) } }

    override fun observeByUnit(unitId: String): Flow<List<PackageItem>> =
        db.packageQueries.observeByUnit(unitId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
            .onStart { bgScope.launch { pullPackagesByUnit(unitId) } }

    override suspend fun register(
        condominiumId: String, unitId: String, description: String,
        carrier: String?, registeredByUserId: String,
    ): AppResult<PackageItem> {
        val id = newId()
        val now = clock.now()
        val unitIdentifier = db.condominiumQueries.getUnit(unitId).executeAsOneOrNull()?.identifier ?: ""
        val item = PackageItem(
            id = id, condominiumId = condominiumId, unitId = unitId, unitIdentifier = unitIdentifier,
            description = description, carrier = carrier, status = PackageStatus.RECEIVED,
            receivedAt = now, pickedUpAt = null, registeredByUserId = registeredByUserId,
        )
        db.packageQueries.upsertPackage(
            id = id, condominiumId = condominiumId, unitId = unitId, unitIdentifier = unitIdentifier,
            description = description, carrier = carrier, status = "RECEIVED",
            receivedAt = now.toEpoch(), pickedUpAt = null,
            registeredByUserId = registeredByUserId, updatedAt = now.toEpoch(), version = 0, deleted = 0,
        )
        dispatcher.enqueue(Entities.PACKAGE, Ops.CREATE, id,
            json.encodeToString(RegisterPackagePayload.serializer(),
                RegisterPackagePayload(id, condominiumId, unitId, description, carrier)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(item)
    }

    override suspend fun markPickedUp(id: String): AppResult<PackageItem> {
        val now = clock.now().toEpoch()
        val row = db.packageQueries.getPackage(id).executeAsOneOrNull()
            ?: return AppResult.Failure(br.tec.wrcoder.meucondominio.core.AppError.NotFound("Encomenda não encontrada"))
        db.packageQueries.upsertPackage(
            id = row.id, condominiumId = row.condominiumId, unitId = row.unitId,
            unitIdentifier = row.unitIdentifier, description = row.description, carrier = row.carrier,
            status = "PICKED_UP", receivedAt = row.receivedAt, pickedUpAt = now,
            registeredByUserId = row.registeredByUserId, updatedAt = now, version = row.version, deleted = 0,
        )
        dispatcher.enqueue(Entities.PACKAGE, Ops.PICKUP, id,
            json.encodeToString(PickupPayload.serializer(), PickupPayload(id)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(db.packageQueries.getPackage(id).executeAsOne().toDomain())
    }

    override fun observeDescriptions(condominiumId: String): Flow<List<PackageDescription>> =
        db.packageQueries.observeDescriptions(condominiumId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { PackageDescription(it.id, it.condominiumId, it.text) } }
            .onStart { bgScope.launch { pullDescriptions(condominiumId) } }

    override suspend fun createDescription(condominiumId: String, text: String): AppResult<PackageDescription> {
        val id = newId()
        val now = clock.now().toEpoch()
        db.packageQueries.upsertDescription(id, condominiumId, text, now, 0, 0)
        dispatcher.enqueue(Entities.PACKAGE_DESCRIPTION, Ops.CREATE, id,
            json.encodeToString(CreateDescriptionPayload.serializer(),
                CreateDescriptionPayload(id, condominiumId, text)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(PackageDescription(id, condominiumId, text))
    }

    private suspend fun pullPackages(condominiumId: String) {
        if (!network.isOnline.value) return
        val firstTime = condominiumId !in reconciledPackages
        val since = if (firstTime) null
        else db.syncMetadataQueries.getCursor(SyncCursors.packagesOf(condominiumId))
            .executeAsOneOrNull()?.let { Instant.fromEpochMilliseconds(it).toString() }
        runCatching { api.listByCondominium(condominiumId, since) }.onSuccess { items ->
            if (firstTime) {
                val serverIds = items.map { it.id }.toSet()
                val localIds = db.packageQueries.idsPackagesByCondominium(condominiumId).executeAsList().toSet()
                (localIds - serverIds).forEach { db.packageQueries.deletePackageById(it) }
                reconciledPackages += condominiumId
            }
            items.forEach(::persistPackage)
            items.maxOfOrNull { Instant.parse(it.updatedAt).toEpoch() }?.let {
                db.syncMetadataQueries.upsertCursor(SyncCursors.packagesOf(condominiumId), it)
            }
        }
    }

    private suspend fun pullPackagesByUnit(unitId: String) {
        if (!network.isOnline.value) return
        val firstTime = unitId !in reconciledPackagesByUnit
        val since = if (firstTime) null
        else db.syncMetadataQueries.getCursor(SyncCursors.packagesOfUnit(unitId))
            .executeAsOneOrNull()?.let { Instant.fromEpochMilliseconds(it).toString() }
        runCatching { api.listByUnit(unitId, since) }.onSuccess { items ->
            if (firstTime) reconciledPackagesByUnit += unitId
            items.forEach(::persistPackage)
            items.maxOfOrNull { Instant.parse(it.updatedAt).toEpoch() }?.let {
                db.syncMetadataQueries.upsertCursor(SyncCursors.packagesOfUnit(unitId), it)
            }
        }
    }

    private suspend fun pullDescriptions(condominiumId: String) {
        if (!network.isOnline.value) return
        val firstTime = condominiumId !in reconciledDescriptions
        runCatching { api.listDescriptions(condominiumId) }.onSuccess { items ->
            if (firstTime) {
                val serverIds = items.map { it.id }.toSet()
                val localIds = db.packageQueries.idsDescriptionsByCondominium(condominiumId).executeAsList().toSet()
                (localIds - serverIds).forEach { db.packageQueries.deleteDescriptionById(it) }
                reconciledDescriptions += condominiumId
            }
            items.forEach(::persistDescription)
        }
    }

    private fun persistPackage(dto: PackageItemDto) {
        db.packageQueries.upsertPackage(
            id = dto.id, condominiumId = dto.condominiumId, unitId = dto.unitId,
            unitIdentifier = dto.unitIdentifier, description = dto.description, carrier = dto.carrier,
            status = dto.status,
            receivedAt = Instant.parse(dto.receivedAt).toEpoch(),
            pickedUpAt = dto.pickedUpAt?.let { Instant.parse(it).toEpoch() },
            registeredByUserId = dto.registeredByUserId,
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
        )
    }

    private fun persistDescription(dto: PackageDescriptionDto) {
        db.packageQueries.upsertDescription(
            id = dto.id, condominiumId = dto.condominiumId, text = dto.text,
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
        )
    }

    private fun br.tec.wrcoder.meucondominio.data.local.db.Package_entity.toDomain(): PackageItem =
        PackageItem(
            id = id, condominiumId = condominiumId, unitId = unitId, unitIdentifier = unitIdentifier,
            description = description, carrier = carrier,
            status = PackageStatus.valueOf(status),
            receivedAt = Instant.fromEpochMilliseconds(receivedAt),
            pickedUpAt = pickedUpAt?.let { Instant.fromEpochMilliseconds(it) },
            registeredByUserId = registeredByUserId,
        )
}
