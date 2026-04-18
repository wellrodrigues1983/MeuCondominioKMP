package br.tec.wrcoder.meucondominio.presentation.features.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.Action
import br.tec.wrcoder.meucondominio.domain.model.Listing
import br.tec.wrcoder.meucondominio.domain.model.Permissions
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.CondominiumRepository
import br.tec.wrcoder.meucondominio.domain.repository.ListingRepository
import br.tec.wrcoder.meucondominio.domain.repository.MediaRepository
import br.tec.wrcoder.meucondominio.domain.usecase.RenewListingUseCase
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

data class ListingEditor(
    val title: String = "",
    val description: String = "",
    val price: String = "",
    val imageBytes: ByteArray? = null,
    val visible: Boolean = false,
)

data class MarketplaceUiState(
    val listings: List<Listing> = emptyList(),
    val canCreate: Boolean = false,
    val user: User? = null,
    val editor: ListingEditor = ListingEditor(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MarketplaceViewModel(
    private val listings: ListingRepository,
    private val auth: AuthRepository,
    private val condos: CondominiumRepository,
    private val renewListingUseCase: RenewListingUseCase,
    private val media: MediaRepository,
) : ViewModel() {

    private val user = auth.session.map { it?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _editor = MutableStateFlow(ListingEditor())
    private val _error = MutableStateFlow<String?>(null)

    val state = combine(
        user.flatMapLatest { u ->
            if (u == null) flowOf(emptyList()) else listings.observe(u.condominiumId)
        },
        user,
        _editor,
        _error,
    ) { all, u, editor, error ->
        MarketplaceUiState(
            listings = all,
            canCreate = u != null && Permissions.canPerform(u.role, Action.LISTING_CREATE),
            user = u,
            editor = editor,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MarketplaceUiState())

    fun showCreate() = _editor.update { ListingEditor(visible = true) }
    fun dismiss() = _editor.update { it.copy(visible = false) }
    fun update(transform: ListingEditor.() -> ListingEditor) = _editor.update(transform)

    fun save() {
        val u = user.value ?: return
        val unitId = u.unitId ?: run {
            _error.value = "Você precisa estar vinculado a uma unidade"
            return
        }
        val e = _editor.value
        if (e.title.isBlank()) {
            _error.value = "Informe o título"; return
        }
        if (e.description.isBlank()) {
            _error.value = "Informe a descrição"; return
        }
        val price = e.price.replace(',', '.').toDoubleOrNull()
        if (e.price.isBlank() || price == null || price <= 0.0) {
            _error.value = "Informe um preço válido"; return
        }
        val bytes = e.imageBytes ?: run {
            _error.value = "Selecione uma foto do produto"; return
        }
        viewModelScope.launch {
            val images = when (val up = media.uploadImage(bytes)) {
                is AppResult.Success -> listOf(up.data)
                is AppResult.Failure -> {
                    _error.value = up.error.message; return@launch
                }
            }
            val unit = (condos.listUnits(u.condominiumId) as? AppResult.Success)?.data
                ?.firstOrNull { it.id == unitId }
            val identifier = unit?.identifier ?: ""
            when (val r = listings.create(u.condominiumId, u.id, identifier, e.title, e.description, price, images)) {
                is AppResult.Success -> _editor.value = ListingEditor()
                is AppResult.Failure -> _error.value = r.error.message
            }
        }
    }

    fun close(listing: Listing) {
        val u = user.value ?: return
        viewModelScope.launch { listings.close(listing.id, u.id) }
    }

    fun renew(listing: Listing) {
        val u = user.value ?: return
        viewModelScope.launch {
            val r = renewListingUseCase(listing, u)
            if (r is AppResult.Failure) _error.value = r.error.message
        }
    }

    fun clearError() { _error.value = null }
}
