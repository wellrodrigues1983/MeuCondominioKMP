package br.tec.wrcoder.meucondominio.data.repository.remote

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.logging.AppLogger
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import br.tec.wrcoder.meucondominio.data.mapper.toEpoch
import br.tec.wrcoder.meucondominio.data.remote.ChatApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.ChatMessageDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ChatThreadDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateChatThreadRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.SendMessageRequestDto
import br.tec.wrcoder.meucondominio.data.remote.ws.ChatRealtimeClient
import br.tec.wrcoder.meucondominio.data.remote.ws.ChatRealtimeEvent
import br.tec.wrcoder.meucondominio.data.sync.Entities
import br.tec.wrcoder.meucondominio.data.sync.Ops
import br.tec.wrcoder.meucondominio.data.sync.OutboxDispatcher
import br.tec.wrcoder.meucondominio.domain.model.ChatMessage
import br.tec.wrcoder.meucondominio.domain.model.ChatThread
import br.tec.wrcoder.meucondominio.domain.model.ChatThreadKind
import br.tec.wrcoder.meucondominio.domain.repository.ChatRepository
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
private data class CreateThreadPayload(
    val id: String, val condominiumId: String, val title: String, val participantUserIds: List<String>,
)

@Serializable
private data class SendMessagePayload(
    val id: String, val threadId: String, val text: String,
)

class RemoteChatRepository(
    private val db: MeuCondominioDb,
    private val api: ChatApiService,
    private val dispatcher: OutboxDispatcher,
    private val network: NetworkMonitor,
    private val clock: AppClock,
    private val json: Json,
    private val realtime: ChatRealtimeClient,
) : ChatRepository {

    private val log = AppLogger.withTag("ChatRepo")
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stringListSerializer = ListSerializer(String.serializer())
    private val reconciledThreads = mutableSetOf<String>()
    private val reconciledMessages = mutableSetOf<String>()
    private val _activeCondoId = MutableStateFlow<String?>(null)
    private val _activeThreadId = MutableStateFlow<String?>(null)

    init {
        dispatcher.register(Entities.CHAT_THREAD, Ops.CREATE) { payload, _ ->
            val p = json.decodeFromString(CreateThreadPayload.serializer(), payload)
            val serverThread = api.createThread(
                p.condominiumId,
                CreateChatThreadRequestDto(p.title, p.participantUserIds, clientRefId = p.id),
            )
            if (serverThread.id != p.id) db.chatQueries.deleteThreadById(p.id)
            persistThread(serverThread)
        }
        dispatcher.register(Entities.CHAT_MESSAGE, Ops.CREATE) { payload, _ ->
            val p = json.decodeFromString(SendMessagePayload.serializer(), payload)
            val serverMsg = api.sendMessage(p.threadId, SendMessageRequestDto(p.text, p.id))
            if (serverMsg.id != p.id) db.chatQueries.deleteMessageById(p.id)
            persistMessage(serverMsg)
        }
        realtime.start()
        bgScope.launch {
            realtime.events.collect { event ->
                runCatching {
                    when (event) {
                        is ChatRealtimeEvent.MessageNew -> persistMessage(event.message)
                        is ChatRealtimeEvent.ThreadUpdate -> persistThread(event.thread)
                    }
                }.onFailure { log.w(it) { "realtime event error" } }
            }
        }
        bgScope.launch {
            network.isOnline.drop(1).distinctUntilChanged().filter { it }.collect {
                _activeCondoId.value?.let { runCatching { pullThreads(it) } }
                _activeThreadId.value?.let { runCatching { pullMessages(it) } }
            }
        }
    }

    override fun observeThreads(condominiumId: String, userId: String): Flow<List<ChatThread>> =
        db.chatQueries.observeThreads(condominiumId).asFlow().mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { it.toDomain() }.filter {
                    it.kind == ChatThreadKind.CONDO_GROUP ||
                        it.participantUserIds.isEmpty() ||
                        userId in it.participantUserIds
                }
            }
            .onStart {
                _activeCondoId.value = condominiumId
                bgScope.launch { pullThreads(condominiumId) }
            }

    override fun observeMessages(threadId: String): Flow<List<ChatMessage>> =
        db.chatQueries.observeMessages(threadId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
            .onStart {
                _activeThreadId.value = threadId
                bgScope.launch { pullMessages(threadId) }
            }

    override suspend fun send(threadId: String, senderUserId: String, text: String): AppResult<ChatMessage> {
        val id = newId()
        val now = clock.now()
        val sender = db.userQueries.getUser(senderUserId).executeAsOneOrNull()
        db.chatQueries.upsertMessage(
            id = id, threadId = threadId, senderUserId = senderUserId,
            senderName = sender?.name ?: "", text = text, sentAt = now.toEpoch(),
            updatedAt = now.toEpoch(), version = 0, deleted = 0,
        )
        val thread = db.chatQueries.getThread(threadId).executeAsOneOrNull()
        if (thread != null) {
            db.chatQueries.upsertThread(
                id = thread.id, condominiumId = thread.condominiumId, title = thread.title,
                participantsJson = thread.participantsJson, lastMessagePreview = text.take(80),
                lastMessageAt = now.toEpoch(), updatedAt = now.toEpoch(),
                version = thread.version, deleted = 0, kind = thread.kind,
            )
        }
        dispatcher.enqueue(Entities.CHAT_MESSAGE, Ops.CREATE, id,
            json.encodeToString(SendMessagePayload.serializer(), SendMessagePayload(id, threadId, text)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        val msg = ChatMessage(id, threadId, senderUserId, sender?.name ?: "", text, now)
        return AppResult.Success(msg)
    }

    override suspend fun openOrCreateThread(
        condominiumId: String, title: String, participantUserIds: List<String>,
    ): AppResult<ChatThread> {
        val existingThreads = db.chatQueries.observeThreads(condominiumId).executeAsList()
            .filter { th ->
                val parts = try { json.decodeFromString(stringListSerializer, th.participantsJson) }
                catch (_: Throwable) { emptyList() }
                th.kind == ChatThreadKind.DIRECT.name &&
                    parts.toSet() == participantUserIds.toSet()
            }
        val existing = existingThreads.firstOrNull()
        if (existing != null) return AppResult.Success(existing.toDomain())
        val id = newId()
        val now = clock.now().toEpoch()
        db.chatQueries.upsertThread(
            id = id, condominiumId = condominiumId, title = title,
            participantsJson = json.encodeToString(stringListSerializer, participantUserIds),
            lastMessagePreview = null, lastMessageAt = null, updatedAt = now,
            version = 0, deleted = 0, kind = ChatThreadKind.DIRECT.name,
        )
        dispatcher.enqueue(Entities.CHAT_THREAD, Ops.CREATE, id,
            json.encodeToString(CreateThreadPayload.serializer(),
                CreateThreadPayload(id, condominiumId, title, participantUserIds)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(
            ChatThread(id, condominiumId, title, participantUserIds, null, null, ChatThreadKind.DIRECT)
        )
    }

    override suspend fun refreshThreads(condominiumId: String) = pullThreads(condominiumId)

    override suspend fun refreshMessages(threadId: String) = pullMessages(threadId)

    override suspend fun ensureCondoGroup(condominiumId: String): AppResult<ChatThread> {
        val local = db.chatQueries.observeCondoGroup(condominiumId).executeAsOneOrNull()
        if (network.isOnline.value) {
            val result = runCatching { api.ensureCondoGroup(condominiumId) }
            result.onSuccess { dto ->
                if (local != null && local.id != dto.id) db.chatQueries.deleteThreadById(local.id)
                persistThread(dto)
                val refreshed = db.chatQueries.getThread(dto.id).executeAsOneOrNull()
                if (refreshed != null) return AppResult.Success(refreshed.toDomain())
            }
            result.onFailure { log.w(it) { "ensureCondoGroup API failed" } }
        }
        if (local != null) return AppResult.Success(local.toDomain())
        val id = newId()
        val now = clock.now().toEpoch()
        val members = db.userQueries.listUsersByCondominium(condominiumId)
            .executeAsList().map { it.id }
        db.chatQueries.upsertThread(
            id = id, condominiumId = condominiumId, title = "Grupo do condomínio",
            participantsJson = json.encodeToString(stringListSerializer, members),
            lastMessagePreview = null, lastMessageAt = null, updatedAt = now,
            version = 0, deleted = 0, kind = ChatThreadKind.CONDO_GROUP.name,
        )
        val created = db.chatQueries.getThread(id).executeAsOneOrNull()
            ?: return AppResult.Failure(
                br.tec.wrcoder.meucondominio.core.AppError.Unknown("Falha ao criar grupo local")
            )
        return AppResult.Success(created.toDomain())
    }

    private suspend fun pullThreads(condominiumId: String) {
        if (!network.isOnline.value) return
        val firstTime = condominiumId !in reconciledThreads
        val since = if (firstTime) null
        else db.chatQueries.lastThreadUpdatedAt(condominiumId).executeAsOneOrNull()?.MAX
            ?.let { Instant.fromEpochMilliseconds(it).toString() }
        val result = runCatching { api.listThreads(condominiumId, since) }
        result.onSuccess { items ->
            if (firstTime) {
                val serverIds = items.map { it.id }.toSet()
                val localDirectIds = db.chatQueries
                    .idsDirectThreadsByCondominium(condominiumId).executeAsList().toSet()
                (localDirectIds - serverIds).forEach { db.chatQueries.deleteThreadById(it) }
                reconciledThreads += condominiumId
            }
            items.forEach(::persistThread)
        }
        result.onFailure { log.w(it) { "pullThreads(condo=$condominiumId) failed" } }
    }

    private suspend fun pullMessages(threadId: String) {
        if (!network.isOnline.value) return
        val since = db.chatQueries.lastMessageUpdatedAt(threadId).executeAsOneOrNull()?.MAX
            ?.let { Instant.fromEpochMilliseconds(it).toString() }
        val result = runCatching { api.listMessages(threadId, since) }
        result.onSuccess { items ->
            reconciledMessages += threadId
            items.forEach(::persistMessage)
        }
        result.onFailure { err ->
            if (err is ClientRequestException && err.response.status == HttpStatusCode.NotFound) {
                purgeGhostThread(threadId)
            } else {
                log.w(err) { "pullMessages(thread=$threadId) failed" }
            }
        }
    }

    private fun purgeGhostThread(threadId: String) {
        val pending = db.outboxQueries
            .countPendingForEntity(Entities.CHAT_THREAD, threadId).executeAsOne()
        if (pending > 0) return
        db.chatQueries.deleteMessagesByThread(threadId)
        db.chatQueries.deleteThreadById(threadId)
        reconciledMessages.remove(threadId)
        if (_activeThreadId.value == threadId) _activeThreadId.value = null
        log.i { "purged ghost thread=$threadId (server returned 404)" }
    }

    private fun persistThread(dto: ChatThreadDto) {
        db.chatQueries.upsertThread(
            id = dto.id, condominiumId = dto.condominiumId, title = dto.title,
            participantsJson = json.encodeToString(stringListSerializer, dto.participantUserIds),
            lastMessagePreview = dto.lastMessagePreview,
            lastMessageAt = dto.lastMessageAt?.let { Instant.parse(it).toEpoch() },
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
            kind = dto.kind.ifBlank { ChatThreadKind.DIRECT.name },
        )
    }

    private fun persistMessage(dto: ChatMessageDto) {
        db.chatQueries.upsertMessage(
            id = dto.id, threadId = dto.threadId, senderUserId = dto.senderUserId,
            senderName = dto.senderName, text = dto.text,
            sentAt = Instant.parse(dto.sentAt).toEpoch(),
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
        )
    }

    private fun br.tec.wrcoder.meucondominio.data.local.db.Chat_thread_entity.toDomain(): ChatThread {
        val parts = try { json.decodeFromString(stringListSerializer, participantsJson) }
                    catch (_: Throwable) { emptyList() }
        val parsedKind = runCatching { ChatThreadKind.valueOf(kind) }.getOrDefault(ChatThreadKind.DIRECT)
        return ChatThread(
            id = id, condominiumId = condominiumId, title = title,
            participantUserIds = parts, lastMessagePreview = lastMessagePreview,
            lastMessageAt = lastMessageAt?.let { Instant.fromEpochMilliseconds(it) },
            kind = parsedKind,
        )
    }

    private fun br.tec.wrcoder.meucondominio.data.local.db.Chat_message_entity.toDomain(): ChatMessage =
        ChatMessage(
            id = id, threadId = threadId, senderUserId = senderUserId,
            senderName = senderName, text = text,
            sentAt = Instant.fromEpochMilliseconds(sentAt),
        )
}
