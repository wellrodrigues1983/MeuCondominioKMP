package br.tec.wrcoder.meucondominio.presentation.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.BinaryStore
import br.tec.wrcoder.meucondominio.domain.model.CondoUnit
import br.tec.wrcoder.meucondominio.domain.model.CreateMemberInput
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.CondominiumRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemberEditor(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val phone: String = "",
    val visible: Boolean = false,
)

data class ProfileUiState(
    val user: User? = null,
    val unit: CondoUnit? = null,
    val members: List<User> = emptyList(),
    val canManageMembers: Boolean = false,
    val editor: MemberEditor = MemberEditor(),
    val error: String? = null,
)

class ProfileViewModel(
    private val auth: AuthRepository,
    private val condos: CondominiumRepository,
    private val binaryStore: BinaryStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            auth.session.collect { session ->
                val u = session?.user
                _state.update { it.copy(user = u, canManageMembers = u?.role == UserRole.RESIDENT && u.unitId != null) }
                if (u?.unitId != null) {
                    val unit = (condos.listUnits(u.condominiumId) as? AppResult.Success)?.data?.firstOrNull { it.id == u.unitId }
                    val members = (auth.listUnitMembers(u.unitId) as? AppResult.Success)?.data.orEmpty()
                    _state.update { it.copy(unit = unit, members = members) }
                }
            }
        }
    }

    fun showAddMember() = _state.update { it.copy(editor = MemberEditor(visible = true)) }
    fun dismiss() = _state.update { it.copy(editor = it.editor.copy(visible = false)) }
    fun updateEditor(transform: MemberEditor.() -> MemberEditor) =
        _state.update { it.copy(editor = it.editor.transform()) }

    fun save() {
        val u = _state.value.user ?: return
        val unitId = u.unitId ?: return
        val e = _state.value.editor
        if (e.name.isBlank() || e.email.isBlank() || e.password.isBlank()) {
            _state.update { it.copy(error = "Preencha nome, e-mail e senha") }
            return
        }
        viewModelScope.launch {
            val input = CreateMemberInput(unitId, e.name, e.email, e.password, e.phone.ifBlank { null })
            when (val r = auth.createUnitMember(input)) {
                is AppResult.Success -> {
                    val members = (auth.listUnitMembers(unitId) as? AppResult.Success)?.data.orEmpty()
                    _state.update { it.copy(members = members, editor = MemberEditor()) }
                }
                is AppResult.Failure -> _state.update { it.copy(error = r.error.message) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun onAvatarPicked(bytes: ByteArray) {
        val uid = _state.value.user?.id ?: return
        viewModelScope.launch {
            when (val r = auth.uploadAvatar(uid, bytes, "avatar.jpg", "image/jpeg")) {
                is AppResult.Success -> r.data.avatarUrl?.let { binaryStore.put(it, bytes) }
                is AppResult.Failure -> _state.update { it.copy(error = r.error.message) }
            }
        }
    }
}
