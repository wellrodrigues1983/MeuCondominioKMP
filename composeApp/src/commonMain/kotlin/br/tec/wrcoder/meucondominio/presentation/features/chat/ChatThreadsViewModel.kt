package br.tec.wrcoder.meucondominio.presentation.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.ChatThread
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.data.repository.InMemoryStore
import br.tec.wrcoder.meucondominio.domain.repository.ChatRepository
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import br.tec.wrcoder.meucondominio.presentation.navigation.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatThreadsUiState(
    val threads: List<ChatThread> = emptyList(),
    val me: User? = null,
    val contacts: List<User> = emptyList(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatThreadsViewModel(
    private val chat: ChatRepository,
    private val auth: AuthRepository,
    private val store: InMemoryStore,
    private val navigator: AppNavigator,
) : ViewModel() {

    private val me = auth.session.map { it?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _error = MutableStateFlow<String?>(null)

    val state = combine(
        me.flatMapLatest { u ->
            if (u == null) flowOf(emptyList()) else chat.observeThreads(u.condominiumId, u.id)
        },
        me,
        store.users,
        _error,
    ) { threads, u, users, error ->
        ChatThreadsUiState(
            threads = threads,
            me = u,
            contacts = if (u == null) emptyList()
            else users.filter {
                it.condominiumId == u.condominiumId &&
                    it.id != u.id &&
                    it.role == UserRole.SUPERVISOR
            },
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatThreadsUiState())

    fun openOrCreate(contact: User) {
        val u = me.value ?: return
        viewModelScope.launch {
            val title = "${u.name} · ${contact.name}"
            when (val r = chat.openOrCreateThread(u.condominiumId, title, listOf(u.id, contact.id))) {
                is AppResult.Success -> navigator.go(Route.Chat(r.data.id))
                is AppResult.Failure -> _error.value = r.error.message
            }
        }
    }

    fun open(thread: ChatThread) = navigator.go(Route.Chat(thread.id))

    fun clearError() { _error.value = null }
}
