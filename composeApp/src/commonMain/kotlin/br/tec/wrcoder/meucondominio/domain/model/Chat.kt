package br.tec.wrcoder.meucondominio.domain.model

import kotlinx.datetime.Instant

data class ChatThread(
    val id: String,
    val condominiumId: String,
    val title: String,
    val participantUserIds: List<String>,
    val lastMessagePreview: String? = null,
    val lastMessageAt: Instant? = null,
)

data class ChatMessage(
    val id: String,
    val threadId: String,
    val senderUserId: String,
    val senderName: String,
    val text: String,
    val sentAt: Instant,
)
