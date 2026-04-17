package br.tec.wrcoder.meucondominio.presentation.features.notices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.Action
import br.tec.wrcoder.meucondominio.domain.model.Notice
import br.tec.wrcoder.meucondominio.domain.model.Permissions
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.NoticeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class NoticeEditor(
    val editingId: String? = null,
    val title: String = "",
    val description: String = "",
    val visible: Boolean = false,
)

data class NoticesUiState(
    val notices: List<Notice> = emptyList(),
    val canManage: Boolean = false,
    val editor: NoticeEditor = NoticeEditor(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class NoticesViewModel(
    private val noticeRepository: NoticeRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val user = authRepository.session.map { it?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _editor = MutableStateFlow(NoticeEditor())
    private val _error = MutableStateFlow<String?>(null)

    val state = kotlinx.coroutines.flow.combine(
        user.flatMapLatest { u ->
            if (u == null) flowOf(emptyList()) else noticeRepository.observe(u.condominiumId)
        },
        user,
        _editor,
        _error,
    ) { notices, u, editor, error ->
        NoticesUiState(
            notices = notices,
            canManage = u != null && Permissions.canPerform(u.role, Action.NOTICE_MANAGE),
            editor = editor,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NoticesUiState())

    fun showCreate() = _editor.update { it.copy(editingId = null, title = "", description = "", visible = true) }

    fun showEdit(notice: Notice) = _editor.update {
        it.copy(editingId = notice.id, title = notice.title, description = notice.description, visible = true)
    }

    fun dismissEditor() = _editor.update { it.copy(visible = false) }

    fun onTitle(v: String) = _editor.update { it.copy(title = v) }
    fun onDescription(v: String) = _editor.update { it.copy(description = v) }

    fun save() {
        val u: User = user.value ?: return
        val e = _editor.value
        viewModelScope.launch {
            val result = if (e.editingId == null) {
                noticeRepository.create(u.condominiumId, u.id, u.name, e.title, e.description)
            } else {
                noticeRepository.update(e.editingId, e.title, e.description)
            }
            when (result) {
                is AppResult.Success -> _editor.value = NoticeEditor()
                is AppResult.Failure -> _error.value = result.error.message
            }
        }
    }

    fun delete(notice: Notice) {
        viewModelScope.launch {
            noticeRepository.delete(notice.id)
        }
    }

    fun clearError() { _error.value = null }
}
