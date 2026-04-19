package br.tec.wrcoder.meucondominio.presentation.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.domain.model.ChatMessage
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.ChatRepository
import br.tec.wrcoder.meucondominio.domain.repository.UserDirectory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatThreadUiState(
    val messages: List<ChatMessage> = emptyList(),
    val me: User? = null,
    val usersById: Map<String, User> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatThreadViewModel(
    private val chat: ChatRepository,
    private val auth: AuthRepository,
    private val users: UserDirectory,
) : ViewModel() {

    private val me = auth.session.map { it?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _state = MutableStateFlow(ChatThreadUiState())
    val state = _state.asStateFlow()

    private var currentThreadId: String? = null

    fun bind(threadId: String) {
        if (currentThreadId == threadId) return
        currentThreadId = threadId
        viewModelScope.launch {
            val peopleFlow = me.flatMapLatest { u ->
                if (u == null) flowOf(emptyList()) else users.observeUsers(u.condominiumId)
            }
            combine(chat.observeMessages(threadId), peopleFlow) { msgs, people -> msgs to people }
                .collect { (msgs, people) ->
                    _state.update {
                        it.copy(
                            messages = msgs,
                            me = me.value,
                            usersById = people.associateBy { u -> u.id },
                        )
                    }
                }
        }
        viewModelScope.launch {
            while (isActive && currentThreadId == threadId) {
                delay(3_000)
                runCatching { chat.refreshMessages(threadId) }
            }
        }
    }

    fun send(text: String) {
        val threadId = currentThreadId ?: return
        val u = me.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            chat.send(threadId, u.id, trimmed)
        }
    }
}
