package br.tec.wrcoder.meucondominio.presentation.features.polls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.Action
import br.tec.wrcoder.meucondominio.domain.model.Permissions
import br.tec.wrcoder.meucondominio.domain.model.Poll
import br.tec.wrcoder.meucondominio.domain.model.PollResults
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.PollsRepository
import br.tec.wrcoder.meucondominio.domain.usecase.VoteOnPollUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

data class PollEditor(
    val question: String = "",
    val options: String = "",
    val durationDays: String = "7",
    val visible: Boolean = false,
)

data class PollsUiState(
    val polls: List<Poll> = emptyList(),
    val results: Map<String, PollResults> = emptyMap(),
    val votedPollIds: Set<String> = emptySet(),
    val canManage: Boolean = false,
    val canVote: Boolean = false,
    val user: User? = null,
    val editor: PollEditor = PollEditor(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PollsViewModel(
    private val polls: PollsRepository,
    private val auth: AuthRepository,
    private val voteOnPollUseCase: VoteOnPollUseCase,
    private val clock: AppClock,
) : ViewModel() {

    private val user = auth.session.map { it?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _editor = MutableStateFlow(PollEditor())
    private val _error = MutableStateFlow<String?>(null)
    private val _results = MutableStateFlow<Map<String, PollResults>>(emptyMap())
    private val _votedIds = MutableStateFlow<Set<String>>(emptySet())

    val state = combine(
        user.flatMapLatest { u -> if (u == null) flowOf(emptyList()) else polls.observe(u.condominiumId) },
        user,
        _editor,
        _error,
        combine(_results, _votedIds) { r, v -> r to v },
    ) { list, u, editor, error, rv ->
        PollsUiState(
            polls = list,
            results = rv.first,
            votedPollIds = rv.second,
            canManage = u != null && Permissions.canPerform(u.role, Action.POLL_MANAGE),
            canVote = u != null && Permissions.canPerform(u.role, Action.POLL_VOTE),
            user = u,
            editor = editor,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PollsUiState())

    fun loadResultsAndVoted() {
        val u = user.value ?: return
        viewModelScope.launch {
            val current = state.value.polls
            _results.value = current.associate { poll ->
                poll.id to ((polls.results(poll.id) as? AppResult.Success)?.data
                    ?: PollResults(poll.id, 0, emptyMap()))
            }
            _votedIds.value = current.filter { polls.hasVoted(it.id, u.id) }.map { it.id }.toSet()
        }
    }

    fun showCreate() = _editor.update { PollEditor(visible = true) }
    fun dismiss() = _editor.update { it.copy(visible = false) }
    fun update(transform: PollEditor.() -> PollEditor) = _editor.update(transform)

    fun save() {
        val u = user.value ?: return
        val e = _editor.value
        val options = e.options.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val days = e.durationDays.toIntOrNull() ?: 7
        val now: Instant = clock.now()
        viewModelScope.launch {
            val r = polls.create(
                condominiumId = u.condominiumId,
                question = e.question,
                options = options,
                startsAt = now,
                endsAt = now + days.days,
                createdByUserId = u.id,
            )
            when (r) {
                is AppResult.Success -> _editor.value = PollEditor()
                is AppResult.Failure -> _error.value = r.error.message
            }
        }
    }

    fun cancel(poll: Poll) {
        viewModelScope.launch { polls.cancel(poll.id) }
    }

    fun vote(poll: Poll, optionId: String) {
        val u = user.value ?: return
        viewModelScope.launch {
            val r = voteOnPollUseCase(poll, optionId, u.id)
            if (r is AppResult.Failure) _error.value = r.error.message
            else loadResultsAndVoted()
        }
    }

    fun clearError() { _error.value = null }
}
