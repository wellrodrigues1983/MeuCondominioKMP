package br.tec.wrcoder.meucondominio.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import br.tec.wrcoder.meucondominio.presentation.navigation.Route
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeFeature(val title: String, val route: Route)

class HomeViewModel(
    private val auth: AuthRepository,
    private val navigator: AppNavigator,
) : ViewModel() {

    val user = auth.session.map { it?.user }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun featuresFor(user: User?): List<HomeFeature> {
        val role = user?.role ?: return emptyList()
        val all = mutableListOf(
            HomeFeature("Avisos", Route.Notices),
            HomeFeature("Encomendas", Route.Packages),
            HomeFeature("Espaços", Route.Spaces),
            HomeFeature("Anúncios", Route.Marketplace),
            HomeFeature("Mudanças", Route.Moving),
            HomeFeature("Arquivos", Route.Files),
            HomeFeature("Enquetes", Route.Polls),
            HomeFeature("Chat", Route.ChatThreads),
            HomeFeature("Perfil", Route.Profile),
        )
        return when (role) {
            UserRole.ADMIN, UserRole.SUPERVISOR -> all
            UserRole.RESIDENT -> all
        }
    }

    fun go(route: Route) = navigator.go(route)

    fun logout() {
        viewModelScope.launch {
            auth.logout()
            navigator.resetTo(Route.Login)
        }
    }
}
