package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.core.asSuccess
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.domain.model.ChatMessage
import br.tec.wrcoder.meucondominio.domain.model.ChatThread
import br.tec.wrcoder.meucondominio.domain.model.ChatThreadKind
import br.tec.wrcoder.meucondominio.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FakeChatRepository(
    private val store: InMemoryStore,
    private val clock: AppClock,
) : ChatRepository {

    override fun observeThreads(condominiumId: String, userId: String): Flow<List<ChatThread>> =
        store.chatThreads.map { all ->
            all.filter { it.condominiumId == condominiumId && userId in it.participantUserIds }
                .sortedByDescending { it.lastMessageAt?.toEpochMilliseconds() ?: 0L }
        }

    override fun observeMessages(threadId: String): Flow<List<ChatMessage>> =
        store.chatMessages.map { all -> all.filter { it.threadId == threadId }.sortedBy { it.sentAt } }

    override suspend fun send(threadId: String, senderUserId: String, text: String): AppResult<ChatMessage> {
        if (text.isBlank()) return AppError.Validation("Mensagem vazia").asFailure()
        val sender = store.users.value.firstOrNull { it.id == senderUserId }
            ?: return AppError.NotFound("Remetente não encontrado").asFailure()
        val message = ChatMessage(
            id = newId(),
            threadId = threadId,
            senderUserId = senderUserId,
            senderName = sender.name,
            text = text.trim(),
            sentAt = clock.now(),
        )
        store.chatMessages.value = store.chatMessages.value + message
        store.chatThreads.value = store.chatThreads.value.map {
            if (it.id == threadId) it.copy(lastMessagePreview = message.text, lastMessageAt = message.sentAt) else it
        }
        return message.asSuccess()
    }

    override suspend fun openOrCreateThread(
        condominiumId: String,
        title: String,
        participantUserIds: List<String>,
    ): AppResult<ChatThread> {
        val existing = store.chatThreads.value.firstOrNull {
            it.condominiumId == condominiumId &&
                it.kind == ChatThreadKind.DIRECT &&
                it.participantUserIds.toSet() == participantUserIds.toSet()
        }
        if (existing != null) return existing.asSuccess()
        val thread = ChatThread(
            id = newId(),
            condominiumId = condominiumId,
            title = title,
            participantUserIds = participantUserIds,
            kind = ChatThreadKind.DIRECT,
        )
        store.chatThreads.value = store.chatThreads.value + thread
        return thread.asSuccess().also { @Suppress("UNUSED_EXPRESSION") clock.now() }
    }

    override suspend fun refreshThreads(condominiumId: String) { /* in-memory, no-op */ }

    override suspend fun refreshMessages(threadId: String) { /* in-memory, no-op */ }

    override suspend fun ensureCondoGroup(condominiumId: String): AppResult<ChatThread> {
        val existing = store.chatThreads.value.firstOrNull {
            it.condominiumId == condominiumId && it.kind == ChatThreadKind.CONDO_GROUP
        }
        if (existing != null) {
            val members = store.users.value.filter { it.condominiumId == condominiumId }.map { it.id }
            if (members.toSet() != existing.participantUserIds.toSet()) {
                val updated = existing.copy(participantUserIds = members)
                store.chatThreads.value = store.chatThreads.value.map {
                    if (it.id == existing.id) updated else it
                }
                return updated.asSuccess()
            }
            return existing.asSuccess()
        }
        val members = store.users.value.filter { it.condominiumId == condominiumId }.map { it.id }
        val thread = ChatThread(
            id = newId(),
            condominiumId = condominiumId,
            title = "Grupo do condomínio",
            participantUserIds = members,
            kind = ChatThreadKind.CONDO_GROUP,
        )
        store.chatThreads.value = store.chatThreads.value + thread
        return thread.asSuccess()
    }
}
