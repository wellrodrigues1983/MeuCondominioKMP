package br.tec.wrcoder.meucondominio.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.JoinCondominiumInput
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.CondominiumRepository
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import br.tec.wrcoder.meucondominio.presentation.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinCondominiumState(
    val code: String = "",
    val unitIdentifier: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val condominiumName: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class JoinCondominiumViewModel(
    private val authRepository: AuthRepository,
    private val condominiums: CondominiumRepository,
    private val navigator: AppNavigator,
) : ViewModel() {
    private val _state = MutableStateFlow(JoinCondominiumState())
    val state = _state.asStateFlow()

    fun update(transform: JoinCondominiumState.() -> JoinCondominiumState) = _state.update(transform)

    fun validateCode() {
        val code = _state.value.code.trim()
        if (code.isBlank()) return
        viewModelScope.launch {
            when (val r = condominiums.findByCode(code)) {
                is AppResult.Success -> _state.update { it.copy(condominiumName = r.data.name, error = null) }
                is AppResult.Failure -> _state.update { it.copy(condominiumName = null, error = r.error.message) }
            }
        }
    }

    fun submit() {
        val s = _state.value
        if (listOf(s.code, s.unitIdentifier, s.name, s.email, s.password).any { it.isBlank() }) {
            _state.update { it.copy(error = "Preencha todos os campos obrigatórios") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val input = JoinCondominiumInput(
                condoCode = s.code.trim(),
                unitIdentifier = s.unitIdentifier.trim(),
                name = s.name.trim(),
                email = s.email.trim(),
                password = s.password,
                phone = s.phone.trim().ifEmpty { null },
            )
            when (val r = authRepository.joinCondominium(input)) {
                is AppResult.Success -> {
                    _state.update { it.copy(loading = false) }
                    navigator.resetTo(Route.Home)
                }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = r.error.message) }
            }
        }
    }

    fun back() { navigator.back() }
}
