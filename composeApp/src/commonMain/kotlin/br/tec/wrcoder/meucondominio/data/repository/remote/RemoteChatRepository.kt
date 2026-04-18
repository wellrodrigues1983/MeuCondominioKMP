package br.tec.wrcoder.meucondominio.data.repository.remote

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.data.local.db.MeuCondominioDb
import br.tec.wrcoder.meucondominio.data.mapper.toEpoch
import br.tec.wrcoder.meucondominio.data.remote.ChatApiService
import br.tec.wrcoder.meucondominio.data.remote.dto.ChatMessageDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ChatThreadDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateChatThreadRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.SendMessageRequestDto
import br.tec.wrcoder.meucondominio.data.sync.Entities
import br.tec.wrcoder.meucondominio.data.sync.Ops
import br.tec.wrcoder.meucondominio.data.sync.OutboxDispatcher
import br.tec.wrcoder.meucondominio.data.sync.SyncCursors
import br.tec.wrcoder.meucondominio.domain.model.ChatMessage
import br.tec.wrcoder.meucondominio.domain.model.ChatThread
import br.tec.wrcoder.meucondominio.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
) : ChatRepository {

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stringListSerializer = ListSerializer(String.serializer())
    private val reconciledThreads = mutableSetOf<String>()
    private val reconciledMessages = mutableSetOf<String>()

    init {
        dispatcher.register(Entities.CHAT_THREAD, Ops.CREATE) { payload, _ ->
            val p = json.decodeFromString(CreateThreadPayload.serializer(), payload)
            persistThread(api.createThread(p.condominiumId,
                CreateChatThreadRequestDto(p.title, p.participantUserIds)))
        }
        dispatcher.register(Entities.CHAT_MESSAGE, Ops.CREATE) { payload, _ ->
            val p = json.decodeFromString(SendMessagePayload.serializer(), payload)
            persistMessage(api.sendMessage(p.threadId, SendMessageRequestDto(p.text, p.id)))
        }
    }

    override fun observeThreads(condominiumId: String, userId: String): Flow<List<ChatThread>> =
        db.chatQueries.observeThreads(condominiumId).asFlow().mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { it.toDomain() }
                    .filter { it.participantUserIds.isEmpty() || userId in it.participantUserIds }
            }
            .onStart { bgScope.launch { pullThreads(condominiumId) } }

    override fun observeMessages(threadId: String): Flow<List<ChatMessage>> =
        db.chatQueries.observeMessages(threadId).asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }
            .onStart { bgScope.launch { pullMessages(threadId) } }

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
                version = thread.version, deleted = 0,
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
            version = 0, deleted = 0,
        )
        dispatcher.enqueue(Entities.CHAT_THREAD, Ops.CREATE, id,
            json.encodeToString(CreateThreadPayload.serializer(),
                CreateThreadPayload(id, condominiumId, title, participantUserIds)))
        if (network.isOnline.value) runCatching { dispatcher.drain() }
        return AppResult.Success(
            ChatThread(id, condominiumId, title, participantUserIds, null, null)
        )
    }

    private suspend fun pullThreads(condominiumId: String) {
        if (!network.isOnline.value) return
        val firstTime = condominiumId !in reconciledThreads
        val since = if (firstTime) null
        else db.syncMetadataQueries.getCursor(SyncCursors.chatThreadsOf(condominiumId))
            .executeAsOneOrNull()?.let { Instant.fromEpochMilliseconds(it).toString() }
        runCatching { api.listThreads(condominiumId, since) }.onSuccess { items ->
            if (firstTime) {
                val serverIds = items.map { it.id }.toSet()
                val localIds = db.chatQueries.idsThreadsByCondominium(condominiumId).executeAsList().toSet()
                (localIds - serverIds).forEach { db.chatQueries.deleteThreadById(it) }
                reconciledThreads += condominiumId
            }
            items.forEach(::persistThread)
            items.maxOfOrNull { Instant.parse(it.updatedAt).toEpoch() }?.let {
                db.syncMetadataQueries.upsertCursor(SyncCursors.chatThreadsOf(condominiumId), it)
            }
        }
    }

    private suspend fun pullMessages(threadId: String) {
        if (!network.isOnline.value) return
        val firstTime = threadId !in reconciledMessages
        val since = if (firstTime) null
        else db.syncMetadataQueries.getCursor(SyncCursors.chatMessagesOf(threadId))
            .executeAsOneOrNull()?.let { Instant.fromEpochMilliseconds(it).toString() }
        runCatching { api.listMessages(threadId, since) }.onSuccess { items ->
            if (firstTime) {
                val serverIds = items.map { it.id }.toSet()
                val localIds = db.chatQueries.idsMessagesByThread(threadId).executeAsList().toSet()
                (localIds - serverIds).forEach { db.chatQueries.deleteMessageById(it) }
                reconciledMessages += threadId
            }
            items.forEach(::persistMessage)
            items.maxOfOrNull { Instant.parse(it.updatedAt).toEpoch() }?.let {
                db.syncMetadataQueries.upsertCursor(SyncCursors.chatMessagesOf(threadId), it)
            }
        }
    }

    private fun persistThread(dto: ChatThreadDto) {
        db.chatQueries.upsertThread(
            id = dto.id, condominiumId = dto.condominiumId, title = dto.title,
            participantsJson = json.encodeToString(stringListSerializer, dto.participantUserIds),
            lastMessagePreview = dto.lastMessagePreview,
            lastMessageAt = dto.lastMessageAt?.let { Instant.parse(it).toEpoch() },
            updatedAt = Instant.parse(dto.updatedAt).toEpoch(),
            version = dto.version, deleted = if (dto.deleted) 1L else 0L,
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
        return ChatThread(
            id = id, condominiumId = condominiumId, title = title,
            participantUserIds = parts, lastMessagePreview = lastMessagePreview,
            lastMessageAt = lastMessageAt?.let { Instant.fromEpochMilliseconds(it) },
        )
    }

    private fun br.tec.wrcoder.meucondominio.data.local.db.Chat_message_entity.toDomain(): ChatMessage =
        ChatMessage(
            id = id, threadId = threadId, senderUserId = senderUserId,
            senderName = senderName, text = text,
            sentAt = Instant.fromEpochMilliseconds(sentAt),
        )
}
