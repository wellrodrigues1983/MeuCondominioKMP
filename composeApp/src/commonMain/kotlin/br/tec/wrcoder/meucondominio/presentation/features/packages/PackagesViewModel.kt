package br.tec.wrcoder.meucondominio.presentation.features.packages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.Action
import br.tec.wrcoder.meucondominio.domain.model.CondoUnit
import br.tec.wrcoder.meucondominio.domain.model.PackageDescription
import br.tec.wrcoder.meucondominio.domain.model.PackageItem
import br.tec.wrcoder.meucondominio.domain.model.Permissions
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.CondominiumRepository
import br.tec.wrcoder.meucondominio.domain.repository.PackageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PackageEditor(
    val unitId: String = "",
    val description: String = "",
    val carrier: String = "",
    val visible: Boolean = false,
    val creatingDescription: Boolean = false,
    val newDescriptionText: String = "",
)

data class PackagesUiState(
    val items: List<PackageItem> = emptyList(),
    val units: List<CondoUnit> = emptyList(),
    val descriptions: List<PackageDescription> = emptyList(),
    val canRegister: Boolean = false,
    val editor: PackageEditor = PackageEditor(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PackagesViewModel(
    private val packages: PackageRepository,
    private val auth: AuthRepository,
    private val condos: CondominiumRepository,
) : ViewModel() {

    private val user = auth.session.map { it?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _editor = MutableStateFlow(PackageEditor())
    private val _error = MutableStateFlow<String?>(null)

    private val userAndItems = user.flatMapLatest { u ->
        val itemsFlow = when {
            u == null -> flowOf(emptyList<PackageItem>())
            u.role == UserRole.RESIDENT && u.unitId != null -> packages.observeByUnit(u.unitId)
            else -> packages.observeByCondominium(u.condominiumId)
        }
        itemsFlow.map { items -> u to items }
    }

    private val unitsFlow = user.flatMapLatest { u ->
        if (u == null) flowOf(emptyList()) else flow {
            val r = condos.listUnits(u.condominiumId)
            emit(if (r is AppResult.Success) r.data else emptyList())
        }
    }

    private val descriptionsFlow = user.flatMapLatest { u ->
        if (u == null) flowOf(emptyList()) else packages.observeDescriptions(u.condominiumId)
    }

    val state = combine(
        userAndItems,
        unitsFlow,
        descriptionsFlow,
        _editor,
        _error,
    ) { userItems, units, descriptions, editor, error ->
        val u = userItems.first
        val items = userItems.second
        PackagesUiState(
            items = items,
            units = units,
            descriptions = descriptions,
            canRegister = u != null && Permissions.canPerform(u.role, Action.PACKAGE_REGISTER),
            editor = editor,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PackagesUiState())

    fun showRegister() = _editor.update {
        it.copy(
            visible = true,
            description = "",
            carrier = "",
            unitId = "",
            creatingDescription = false,
            newDescriptionText = "",
        )
    }
    fun dismiss() = _editor.update { it.copy(visible = false) }
    fun onDescription(v: String) = _editor.update { it.copy(description = v, creatingDescription = false, newDescriptionText = "") }
    fun onCarrier(v: String) = _editor.update { it.copy(carrier = v) }
    fun onUnit(unitId: String) = _editor.update { it.copy(unitId = unitId) }

    fun startCreateDescription() = _editor.update {
        it.copy(creatingDescription = true, newDescriptionText = "")
    }
    fun onNewDescriptionText(v: String) = _editor.update { it.copy(newDescriptionText = v) }
    fun cancelCreateDescription() = _editor.update {
        it.copy(creatingDescription = false, newDescriptionText = "")
    }
    fun confirmCreateDescription() {
        val u = user.value ?: return
        val text = _editor.value.newDescriptionText
        viewModelScope.launch {
            when (val r = packages.createDescription(u.condominiumId, text)) {
                is AppResult.Success -> _editor.update {
                    it.copy(
                        description = r.data.text,
                        creatingDescription = false,
                        newDescriptionText = "",
                    )
                }
                is AppResult.Failure -> _error.value = r.error.message
            }
        }
    }

    fun save() {
        val u = user.value ?: return
        val e = _editor.value
        if (e.unitId.isBlank() || e.description.isBlank()) {
            _error.value = "Informe unidade e descrição"
            return
        }
        viewModelScope.launch {
            when (val r = packages.register(u.condominiumId, e.unitId, e.description, e.carrier.ifBlank { null }, u.id)) {
                is AppResult.Success -> _editor.value = PackageEditor()
                is AppResult.Failure -> _error.value = r.error.message
            }
        }
    }

    fun markPickedUp(item: PackageItem) {
        viewModelScope.launch { packages.markPickedUp(item.id) }
    }

    fun clearError() { _error.value = null }
}
