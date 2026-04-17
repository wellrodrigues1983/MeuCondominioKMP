package br.tec.wrcoder.meucondominio.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.RegisterCondominiumInput
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import br.tec.wrcoder.meucondominio.presentation.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterCondominiumState(
    val condominiumName: String = "",
    val address: String = "",
    val adminName: String = "",
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class RegisterCondominiumViewModel(
    private val authRepository: AuthRepository,
    private val navigator: AppNavigator,
) : ViewModel() {
    private val _state = MutableStateFlow(RegisterCondominiumState())
    val state = _state.asStateFlow()

    fun update(transform: RegisterCondominiumState.() -> RegisterCondominiumState) = _state.update(transform)

    fun submit() {
        val s = _state.value
        if (listOf(s.condominiumName, s.address, s.adminName, s.email, s.password).any { it.isBlank() }) {
            _state.update { it.copy(error = "Preencha todos os campos") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val input = RegisterCondominiumInput(
                condominiumName = s.condominiumName.trim(),
                address = s.address.trim(),
                adminName = s.adminName.trim(),
                adminEmail = s.email.trim(),
                adminPassword = s.password,
            )
            when (val r = authRepository.registerCondominium(input)) {
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
