package br.tec.wrcoder.meucondominio.presentation.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.domain.model.ChatMessage
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatThreadUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val me: User? = null,
)

class ChatThreadViewModel(
    private val chat: ChatRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val me = auth.session.map { it?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _state = MutableStateFlow(ChatThreadUiState())
    val state = _state.asStateFlow()

    private var currentThreadId: String? = null

    fun bind(threadId: String) {
        if (currentThreadId == threadId) return
        currentThreadId = threadId
        viewModelScope.launch {
            chat.observeMessages(threadId).collect { messages ->
                _state.update { it.copy(messages = messages, me = me.value) }
            }
        }
    }

    fun onInput(v: String) = _state.update { it.copy(input = v) }

    fun send() {
        val threadId = currentThreadId ?: return
        val u = me.value ?: return
        val text = _state.value.input
        if (text.isBlank()) return
        viewModelScope.launch {
            chat.send(threadId, u.id, text)
            _state.update { it.copy(input = "") }
        }
    }
}
