package br.tec.wrcoder.meucondominio.presentation.features.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.formatBr
import br.tec.wrcoder.meucondominio.domain.model.Action
import br.tec.wrcoder.meucondominio.domain.model.CommonSpace
import br.tec.wrcoder.meucondominio.domain.model.Permissions
import br.tec.wrcoder.meucondominio.domain.model.Reservation
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.MediaRepository
import br.tec.wrcoder.meucondominio.domain.repository.SpaceRepository
import br.tec.wrcoder.meucondominio.domain.usecase.CancelReservationUseCase
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class SpaceEditor(
    val name: String = "",
    val description: String = "",
    val price: String = "",
    val imageBytes: ByteArray? = null,
    val visible: Boolean = false,
)

data class SpacesUiState(
    val spaces: List<CommonSpace> = emptyList(),
    val myReservations: List<Reservation> = emptyList(),
    val canManage: Boolean = false,
    val editor: SpaceEditor = SpaceEditor(),
    val error: String? = null,
    val notice: String? = null,
    val user: User? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SpacesViewModel(
    private val spaceRepository: SpaceRepository,
    private val auth: AuthRepository,
    private val cancelReservationUseCase: CancelReservationUseCase,
    private val navigator: AppNavigator,
    private val media: MediaRepository,
) : ViewModel() {

    private val user = auth.session.map { it?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _editor = MutableStateFlow(SpaceEditor())
    private val _error = MutableStateFlow<String?>(null)
    private val _notice = MutableStateFlow<String?>(null)

    val state = combine(
        user.flatMapLatest { u ->
            if (u == null) flowOf(emptyList()) else spaceRepository.observeSpaces(u.condominiumId)
        },
        user.flatMapLatest { u ->
            if (u?.unitId == null) flowOf(emptyList()) else spaceRepository.observeReservationsByUnit(u.unitId)
        },
        user,
        _editor,
        combine(_error, _notice) { e, n -> e to n },
    ) { spaces, reservations, u, editor, (error, notice) ->
        SpacesUiState(
            spaces = spaces,
            myReservations = reservations,
            canManage = u != null && Permissions.canPerform(u.role, Action.SPACE_MANAGE),
            editor = editor,
            error = error,
            notice = notice,
            user = u,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SpacesUiState())

    fun refresh() {
        val u = user.value ?: return
        viewModelScope.launch { spaceRepository.refreshSpaces(u.condominiumId) }
    }

    fun openDetail(space: CommonSpace) = navigator.go(Route.SpaceDetail(space.id))

    fun showCreate() = _editor.update { SpaceEditor(visible = true) }
    fun dismiss() = _editor.update { it.copy(visible = false) }
    fun update(transform: SpaceEditor.() -> SpaceEditor) = _editor.update(transform)

    fun save() {
        val u = user.value ?: return
        val e = _editor.value
        val price = e.price.replace(',', '.').toDoubleOrNull()
        if (price == null) {
            _error.value = "Valor inválido"
            return
        }
        viewModelScope.launch {
            val images: List<String> = e.imageBytes?.let { bytes ->
                when (val up = media.uploadImage(bytes)) {
                    is AppResult.Success -> listOf(up.data)
                    is AppResult.Failure -> {
                        _error.value = up.error.message; return@launch
                    }
                }
            } ?: emptyList()
            when (val r = spaceRepository.createSpace(u.condominiumId, e.name, e.description, price, images)) {
                is AppResult.Success -> _editor.value = SpaceEditor()
                is AppResult.Failure -> _error.value = r.error.message
            }
        }
    }

    fun reserve(space: CommonSpace, date: LocalDate) {
        val u = user.value
        println("[Spaces] reserve tap space=${space.id} date=$date user=${u?.id} unitId=${u?.unitId}")
        if (u == null) {
            _error.value = "Sessão inválida. Entre novamente."
            return
        }
        if (u.unitId == null) {
            _error.value = "Apenas moradores vinculados a uma unidade podem reservar"
            return
        }
        viewModelScope.launch {
            val r = spaceRepository.reserve(space.id, u.unitId, u.id, date)
            println("[Spaces] reserve result=$r")
            when (r) {
                is AppResult.Success -> _notice.value = "Reserva de ${space.name} confirmada para ${date.formatBr()}."
                is AppResult.Failure -> _error.value = r.error.message
            }
        }
    }

    fun cancelReservation(reservation: Reservation, reason: String? = null) {
        val u = user.value ?: return
        viewModelScope.launch {
            val r = cancelReservationUseCase(reservation, u, reason)
            if (r is AppResult.Failure) _error.value = r.error.message
        }
    }

    fun clearError() { _error.value = null }
    fun clearNotice() { _notice.value = null }
}
