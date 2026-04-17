package br.tec.wrcoder.meucondominio.presentation.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface Route {
    data object Login : Route
    data object RegisterCondominium : Route
    data object JoinCondominium : Route
    data object Home : Route

    data object Notices : Route
    data object Packages : Route
    data object Spaces : Route
    data class SpaceDetail(val spaceId: String) : Route
    data object Marketplace : Route
    data object Moving : Route
    data object Files : Route
    data object Polls : Route
    data class PollDetail(val pollId: String) : Route
    data object Profile : Route
    data object ChatThreads : Route
    data class Chat(val threadId: String) : Route
}

/**
 * Lightweight state-based navigator. Keeps a back stack and exposes the
 * current destination as a StateFlow. Good enough for the scaffold; if a
 * richer solution is needed, swap for androidx-navigation-compose.
 */
class AppNavigator(initial: Route = Route.Login) {
    private val stack = ArrayDeque<Route>().apply { addLast(initial) }
    private val _current = MutableStateFlow<Route>(initial)
    val current = _current.asStateFlow()

    fun go(route: Route) {
        stack.addLast(route)
        _current.value = route
    }

    fun replace(route: Route) {
        if (stack.isNotEmpty()) stack.removeLast()
        stack.addLast(route)
        _current.value = route
    }

    fun resetTo(route: Route) {
        stack.clear()
        stack.addLast(route)
        _current.value = route
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeLast()
        _current.value = stack.last()
        return true
    }
}
