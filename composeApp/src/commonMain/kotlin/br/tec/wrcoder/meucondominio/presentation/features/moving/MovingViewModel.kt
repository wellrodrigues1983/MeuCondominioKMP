package br.tec.wrcoder.meucondominio.presentation.features.moving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.toLocalDate
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
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

const val MOVING_MAX_DAYS_AHEAD = 30

data class MovingEditor(
    val date: LocalDate? = null,
    val hour: Int = 9,
    val minute: Int = 0,
    val visible: Boolean = false,
)

data class MovingUiState(
    val items: List<MovingRequest> = emptyList(),
    val user: User? = null,
    val canDecide: Boolean = false,
    val canCreate: Boolean = false,
    val editor: MovingEditor = MovingEditor(),
    val today: LocalDate? = null,
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

    private fun today(): LocalDate = clock.now().toLocalDate(clock.timeZone())

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
            today = today(),
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MovingUiState())

    fun showCreate() = _editor.update {
        MovingEditor(date = today(), hour = 9, minute = 0, visible = true)
    }
    fun dismiss() = _editor.update { it.copy(visible = false) }
    fun onDate(date: LocalDate) = _editor.update { it.copy(date = date) }
    fun onTime(hour: Int, minute: Int) = _editor.update { it.copy(hour = hour, minute = minute) }

    fun submit() {
        val u = user.value ?: return
        val unitId = u.unitId ?: return
        val e = _editor.value
        val date = e.date ?: run {
            _error.value = "Selecione uma data"
            return
        }
        val today = today()
        val max = today.plus(DatePeriod(days = MOVING_MAX_DAYS_AHEAD))
        if (date < today || date > max) {
            _error.value = "A data deve estar nos próximos $MOVING_MAX_DAYS_AHEAD dias"
            return
        }
        val scheduled = LocalDateTime(date, LocalTime(e.hour, e.minute))
        viewModelScope.launch {
            val r = moving.request(u.condominiumId, unitId, u.id, scheduled)
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
