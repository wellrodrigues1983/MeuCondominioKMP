package br.tec.wrcoder.meucondominio.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.domain.model.LoginCredentials
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import br.tec.wrcoder.meucondominio.presentation.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val navigator: AppNavigator,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = "Informe e-mail e senha") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val r = authRepository.login(LoginCredentials(s.email.trim(), s.password))) {
                is AppResult.Success -> {
                    _state.update { it.copy(loading = false) }
                    navigator.resetTo(Route.Home)
                }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = r.error.message) }
            }
        }
    }

    fun goToRegisterCondo() = navigator.go(Route.RegisterCondominium)
    fun goToJoinCondo() = navigator.go(Route.JoinCondominium)
}
