package br.tec.wrcoder.meucondominio.presentation.features.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.Action
import br.tec.wrcoder.meucondominio.domain.model.FileDoc
import br.tec.wrcoder.meucondominio.domain.model.Permissions
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.FilesRepository
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

data class FileEditor(
    val title: String = "",
    val description: String = "",
    val fileName: String = "",
    val visible: Boolean = false,
)

data class FilesUiState(
    val files: List<FileDoc> = emptyList(),
    val canManage: Boolean = false,
    val editor: FileEditor = FileEditor(),
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModel(
    private val files: FilesRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val user = auth.session.map { it?.user }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _editor = MutableStateFlow(FileEditor())
    private val _error = MutableStateFlow<String?>(null)

    val state = combine(
        user.flatMapLatest { u -> if (u == null) flowOf(emptyList()) else files.observe(u.condominiumId) },
        user,
        _editor,
        _error,
    ) { list, u, editor, error ->
        FilesUiState(
            files = list,
            canManage = u != null && Permissions.canPerform(u.role, Action.FILE_MANAGE),
            editor = editor,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FilesUiState())

    fun showUpload() = _editor.update { FileEditor(visible = true) }
    fun dismiss() = _editor.update { it.copy(visible = false) }
    fun update(transform: FileEditor.() -> FileEditor) = _editor.update(transform)

    fun submit() {
        val u = user.value ?: return
        val e = _editor.value
        if (!e.fileName.endsWith(".pdf", ignoreCase = true)) {
            _error.value = "Somente arquivos PDF"
            return
        }
        viewModelScope.launch {
            val r = files.upload(u.condominiumId, u.id, e.title, e.description.ifBlank { null }, e.fileName, 0L, ByteArray(0))
            when (r) {
                is AppResult.Success -> _editor.value = FileEditor()
                is AppResult.Failure -> _error.value = r.error.message
            }
        }
    }

    fun delete(file: FileDoc) {
        viewModelScope.launch { files.delete(file.id) }
    }

    fun clearError() { _error.value = null }
}
