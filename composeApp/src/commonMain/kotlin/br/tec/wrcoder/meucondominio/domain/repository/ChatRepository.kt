package br.tec.wrcoder.meucondominio.domain.repository

import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.ChatMessage
import br.tec.wrcoder.meucondominio.domain.model.ChatThread
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeThreads(condominiumId: String, userId: String): Flow<List<ChatThread>>
    fun observeMessages(threadId: String): Flow<List<ChatMessage>>
    suspend fun send(threadId: String, senderUserId: String, text: String): AppResult<ChatMessage>
    suspend fun openOrCreateThread(
        condominiumId: String,
        title: String,
        participantUserIds: List<String>,
    ): AppResult<ChatThread>

    suspend fun ensureCondoGroup(condominiumId: String): AppResult<ChatThread>

    suspend fun refreshThreads(condominiumId: String)
    suspend fun refreshMessages(threadId: String)
}
