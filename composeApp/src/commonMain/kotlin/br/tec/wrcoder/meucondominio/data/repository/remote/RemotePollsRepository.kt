package br.tec.wrcoder.meucondominio.data.repository.remote

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import br.tec.wrcoder.meucondominio.data.mapper.toEpoch
import br.tec.wrcoder.meucondominio.data.remote.PollsApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.CreatePollRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PollDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PollOptionDto
import br.tec.wrcoder.meucondominio.data.remote.dto.VoteRequestDto
import br.tec.wrcoder.meucondominio.data.sync.Entities
import br.tec.wrcoder.meucondominio.data.sync.Ops
import br.tec.wrcoder.meucondominio.data.sync.OutboxDispatcher
import br.tec.wrcoder.meucondominio.data.sync.SyncCursors
import br.tec.wrcoder.meucondominio.domain.model.Poll
import br.tec.wrcoder.meucondominio.domain.model.PollOption
import br.tec.wrcoder.meucondominio.domain.model.PollResults
import br.tec.wrcoder.meucondominio.domain.model.PollStatus
import br.tec.wrcoder.meucondominio.domain.repository.PollsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
private data class CreatePollPayload(
    val id: String, val condominiumId: String, val question: String,
    val options: List<String>, val startsAt: String, val endsAt: String,
)

@Serializable
private data class CancelPollPayload(val id: String)

@Serializable
private data class VotePayload(val pollId: String, val optionId: String, val userId: String)

class RemotePollsRepository(
    private val db: MeuCondominioDb,
    private val api: PollsApiService,
    private val dispatcher: OutboxDispatcher,
    private val network: NetworkMonitor,
    private val clock: AppClock,
    private val json: Json,
) : PollsRepository {

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val optionListSerializer = ListSerializer(PollOptionSerial.serializer())
    private val reconciled = mutableSetOf<String>()

    @Serializable
    private data class PollOptionSerial(val id: String, val text: String)

    init {
        dispatcher.register(Entities.POLL, Ops.CREATE) { payload, _ ->
            val p = json.decodeFromString(CreatePollPayload.serializer(), payload)
            persist(api.create(p.condominiumId,
                CreatePollRequestDto(p.question, p.options, p.startsAt, p.endsAt)))
        }
        dispatcher.register(Entities.POLL, Ops.CANCEL_STAFF) { payload, _ ->
            val p = json.decodeFromString(CancelPollPayload.serializer(), payload)
            persist(api.cancel(p.id))
        }
        dispatcher.register(Entities.POLL_VOTE, Ops.VOTE) { payload, _ ->
            val p = json.decodeFromString(VotePayload.serializer(), payload)
            api.vote(p.pollId, VoteRequestDto(p.optionId))
        }
    }

    override fun observe(condominiumId: String): Flow<List<Poll>> =
        db.pollQueries.observePolls(condominiumId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
            .onStart { bgScope.launch { pull(condominiumId) } }

    override suspend fun create(
        condominiumId: String, question: String, options: List<String>,
        startsAt: Instant, endsAt: Instant, createdByUserId: String,
    ): AppResult<Poll> {
        val id = newId()
        val now = clock.now()
        val opts = options.map { PollOption(newId(), it) }
        db.pollQueries.upsertPoll(
            id = id, condominiumId = condominiumId, question = question,
            optionsJson = json.encodeToString(optionListSerializer,
                opts.map { PollOptionSerial(it.id, it.text) }),
            startsAt = startsAt.toEpoch(), endsAt = endsAt.toEpoch(),
            status = if (startsAt <= now) "OPEN" else "SCHEDULED",
            createdByUserId = createdByUserId, createdAt = now.toEpoch(), updatedAt = now.toEpoch(),
            version = 0, deleted = 0,
        )
        dispatcher.enqueue(Entities.POLL, Ops.CREATE, id,
            json.encodeToString(CreatePollPayload.serializer(),
                CreatePollPayload(id, condominiumId, question, options, startsAt.toString(), endsAt.toString())))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(db.pollQueries.getPoll(id).executeAsOne().toDomain())
    }

    override suspend fun cancel(pollId: String): AppResult<Poll> {
        val row = db.pollQueries.getPoll(pollId).executeAsOneOrNull()
            ?: return AppResult.Failure(AppError.NotFound("Enquete não encontrada"))
        val now = clock.now().toEpoch()
        db.pollQueries.upsertPoll(
            id = row.id, condominiumId = row.condominiumId, question = row.question,
            optionsJson = row.optionsJson, startsAt = row.startsAt, endsAt = row.endsAt,
            status = "CANCELLED", createdByUserId = row.createdByUserId,
            createdAt = row.createdAt, updatedAt = now, version = row.version, deleted = 0,
        )
        dispatcher.enqueue(Entities.POLL, Ops.CANCEL_STAFF, pollId,
            json.encodeToString(CancelPollPayload.serializer(), CancelPollPayload(pollId)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(db.pollQueries.getPoll(pollId).executeAsOne().toDomain())
    }

    override suspend fun vote(pollId: String, optionId: String, userId: String): AppResult<Unit> {
        val now = clock.now().toEpoch()
        db.pollQueries.upsertVote(pollId, optionId, userId, now, now, 0, 0)
        dispatcher.enqueue(Entities.POLL_VOTE, Ops.VOTE, "$pollId:$userId",
            json.encodeToString(VotePayload.serializer(), VotePayload(pollId, optionId, userId)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(Unit)
    }

    override suspend fun hasVoted(pollId: String, userId: String): Boolean =
        db.pollQueries.hasVoted(pollId, userId).executeAsOne() > 0

    override suspend fun results(pollId: String): AppResult<PollResults> {
        if (network.isOnline.value) {
            val r = runRemote { api.results(pollId) }
            if (r is AppResult.Success) {
                return AppResult.Success(PollResults(r.data.pollId, r.data.total, r.data.countsByOptionId))
            }
        }
        val votes = db.pollQueries.listVotes(pollId).executeAsList()
        val counts = votes.groupingBy { it.optionId }.eachCount().mapValues { it.value }
        return AppResult.Success(PollResults(pollId, votes.size, counts))
    }

    private suspend fun pull(condominiumId: String) {
        if (!network.isOnline.value) return
        val firstTime = condominiumId !in reconciled
        val since = if (firstTime) null
        else db.syncMetadataQueries.getCursor(SyncCursors.pollsOf(condominiumId))
            .executeAsOneOrNull()?.let { Instant.fromEpochMilliseconds(it).toString() }
        runCatching { api.list(condominiumId, since) }.onSuccess { items ->
            if (firstTime) {
                val serverIds = items.map { it.id }.toSet()
                val localIds = db.pollQueries.idsPollsByCondominium(condominiumId).executeAsList().toSet()
                (localIds - serverIds).forEach { db.pollQueries.deletePollById(it) }
                reconciled += condominiumId
            }
            items.forEach(::persist)
            items.maxOfOrNull { Instant.parse(it.updatedAt).toEpoch() }?.let {
                db.syncMetadataQueries.upsertCursor(SyncCursors.pollsOf(condominiumId), it)
            }
        }
    }

    private fun persist(dto: PollDto) {
        db.pollQueries.upsertPoll(
            id = dto.id, condominiumId = dto.condominiumId, question = dto.question,
            optionsJson = json.encodeToString(optionListSerializer,
                dto.options.map { PollOptionSerial(it.id, it.text) }),
            startsAt = Instant.parse(dto.startsAt).toEpoch(),
            endsAt = Instant.parse(dto.endsAt).toEpoch(),
            status = dto.status, createdByUserId = dto.createdByUserId,
            createdAt = Instant.parse(dto.createdAt).toEpoch(),
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
        )
    }

    private fun br.tec.wrcoder.meucondominio.data.local.db.Poll_entity.toDomain(): Poll {
        val opts = try {
            json.decodeFromString(optionListSerializer, optionsJson)
                .map { PollOption(it.id, it.text) }
        } catch (_: Throwable) { emptyList() }
        return Poll(
            id = id, condominiumId = condominiumId, question = question,
            options = opts,
            startsAt = Instant.fromEpochMilliseconds(startsAt),
            endsAt = Instant.fromEpochMilliseconds(endsAt),
            status = PollStatus.valueOf(status),
            createdByUserId = createdByUserId,
            createdAt = Instant.fromEpochMilliseconds(createdAt),
        )
    }
}
