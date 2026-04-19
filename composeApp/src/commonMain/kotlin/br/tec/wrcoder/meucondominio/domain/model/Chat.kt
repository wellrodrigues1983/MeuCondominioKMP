package br.tec.wrcoder.meucondominio.domain.model

import kotlinx.datetime.Instant

enum class ChatThreadKind { DIRECT, CONDO_GROUP }

data class ChatThread(
    val id: String,
    val condominiumId: String,
    val title: String,
    val participantUserIds: List<String>,
    val lastMessagePreview: String? = null,
    val lastMessageAt: Instant? = null,
    val kind: ChatThreadKind = ChatThreadKind.DIRECT,
)

data class ChatMessage(
    val id: String,
    val threadId: String,
    val senderUserId: String,
    val senderName: String,
    val text: String,
    val sentAt: Instant,
)
