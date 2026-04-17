package br.tec.wrcoder.meucondominio.presentation.features.moving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.toLocalDateTime
import br.tec.wrcoder.meucondominio.domain.model.Action
import br.tec.wrcoder.meucondominio.domain.model.MovingRequest
import br.tec.wrcoder.meucondominio.domain.model.Permissions
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.MovingRepository
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
import kotlinx.datetime.LocalDateTime

data class MovingEditor(val dateText: String = "", val visible: Boolean = false)

data class MovingUiState(
    val items: List<MovingRequest> = emptyList(),
    val user: User? = null,
    val canDecide: Boolean = false,
    val canCreate: Boolean = false,
    val editor: MovingEditor = MovingEditor(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MovingViewModel(
    private val moving: MovingRepository,
    private val auth: AuthRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val user = auth.session.map { it?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _editor = MutableStateFlow(MovingEditor())
    private val _error = MutableStateFlow<String?>(null)

    val state = combine(
        user.flatMapLatest { u ->
            when {
                u == null -> flowOf(emptyList())
                u.role == UserRole.RESIDENT && u.unitId != null -> moving.observeByUnit(u.unitId)
                else -> moving.observeByCondominium(u.condominiumId)
            }
        },
        user,
        _editor,
        _error,
    ) { items, u, editor, error ->
        MovingUiState(
            items = items,
            user = u,
            canDecide = u != null && Permissions.canPerform(u.role, Action.MOVING_REQUEST_DECIDE),
            canCreate = u != null && Permissions.canPerform(u.role, Action.MOVING_REQUEST_CREATE) && u.unitId != null,
            editor = editor,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MovingUiState())

    fun showCreate() = _editor.update {
        MovingEditor(dateText = clock.now().toLocalDateTime().toString(), visible = true)
    }
    fun dismiss() = _editor.update { it.copy(visible = false) }
    fun onDate(v: String) = _editor.update { it.copy(dateText = v) }

    fun submit() {
        val u = user.value ?: return
        val unitId = u.unitId ?: return
        val date = runCatching { LocalDateTime.parse(_editor.value.dateText) }.getOrNull()
        if (date == null) {
            _error.value = "Data inválida (ex: 2026-05-01T10:00)"
            return
        }
        viewModelScope.launch {
            val r = moving.request(u.condominiumId, unitId, u.id, date)
            if (r is AppResult.Failure) _error.value = r.error.message else _editor.value = MovingEditor()
        }
    }

    fun approve(req: MovingRequest) {
        val u = user.value ?: return
        viewModelScope.launch { moving.approve(req.id, u.id) }
    }

    fun reject(req: MovingRequest, reason: String) {
        val u = user.value ?: return
        viewModelScope.launch { moving.reject(req.id, u.id, reason) }
    }

    fun cancelByStaff(req: MovingRequest, reason: String) {
        val u = user.value ?: return
        viewModelScope.launch { moving.cancelByStaff(req.id, u.id, reason) }
    }

    fun clearError() { _error.value = null }
}
