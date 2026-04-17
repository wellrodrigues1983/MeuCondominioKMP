package br.tec.wrcoder.meucondominio.presentation.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.data.repository.InMemoryStore
import br.tec.wrcoder.meucondominio.domain.model.ChatMessage
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatThreadUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val me: User? = null,
    val usersById: Map<String, User> = emptyMap(),
)

class ChatThreadViewModel(
    private val chat: ChatRepository,
    private val auth: AuthRepository,
    private val store: InMemoryStore,
) : ViewModel() {

    private val me = auth.session.map { it?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _state = MutableStateFlow(ChatThreadUiState())
    val state = _state.asStateFlow()

    private var currentThreadId: String? = null

    fun bind(threadId: String) {
        if (currentThreadId == threadId) return
        currentThreadId = threadId
        viewModelScope.launch {
            combine(chat.observeMessages(threadId), store.users) { msgs, users -> msgs to users }
                .collect { (msgs, users) ->
                    _state.update {
                        it.copy(
                            messages = msgs,
                            me = me.value,
                            usersById = users.associateBy { u -> u.id },
                        )
                    }
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
