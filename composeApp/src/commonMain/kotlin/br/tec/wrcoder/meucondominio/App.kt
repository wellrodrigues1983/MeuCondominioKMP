package br.tec.wrcoder.meucondominio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.presentation.auth.JoinCondominiumScreen
import br.tec.wrcoder.meucondominio.presentation.auth.LoginScreen
import br.tec.wrcoder.meucondominio.presentation.auth.RegisterCondominiumScreen
import br.tec.wrcoder.meucondominio.presentation.features.chat.ChatThreadScreen
import br.tec.wrcoder.meucondominio.presentation.features.chat.ChatThreadsScreen
import br.tec.wrcoder.meucondominio.presentation.features.files.FilesScreen
import br.tec.wrcoder.meucondominio.presentation.features.marketplace.MarketplaceScreen
import br.tec.wrcoder.meucondominio.presentation.features.moving.MovingScreen
import br.tec.wrcoder.meucondominio.presentation.features.notices.NoticesScreen
import br.tec.wrcoder.meucondominio.presentation.features.packages.PackagesScreen
import br.tec.wrcoder.meucondominio.presentation.features.polls.PollsScreen
import br.tec.wrcoder.meucondominio.presentation.features.profile.ProfileScreen
import br.tec.wrcoder.meucondominio.presentation.features.spaces.SpaceDetailScreen
import br.tec.wrcoder.meucondominio.presentation.features.spaces.SpacesScreen
import br.tec.wrcoder.meucondominio.presentation.main.HomeScreen
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import br.tec.wrcoder.meucondominio.presentation.navigation.Route
import br.tec.wrcoder.meucondominio.presentation.theme.AppTheme
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

@Composable
fun App() {
    KoinContext {
        AppTheme {
            val navigator = koinInject<AppNavigator>()
            val auth = koinInject<AuthRepository>()
            val session by auth.session.collectAsStateWithLifecycle(initialValue = null)
            val route by navigator.current.collectAsStateWithLifecycle()

            LaunchedEffect(session) {
                val current = navigator.current.value
                when {
                    session != null && current is Route.Login -> navigator.resetTo(Route.Home)
                    session != null && current is Route.RegisterCondominium -> navigator.resetTo(Route.Home)
                    session != null && current is Route.JoinCondominium -> navigator.resetTo(Route.Home)
                    session == null && current !is Route.Login &&
                        current !is Route.RegisterCondominium && current !is Route.JoinCondominium ->
                        navigator.resetTo(Route.Login)
                }
            }

            when (val r = route) {
                Route.Login -> LoginScreen()
                Route.RegisterCondominium -> RegisterCondominiumScreen()
                Route.JoinCondominium -> JoinCondominiumScreen()
                Route.Home -> HomeScreen()
                Route.Notices -> NoticesScreen()
                Route.Packages -> PackagesScreen()
                Route.Spaces -> SpacesScreen()
                is Route.SpaceDetail -> SpaceDetailScreen(r.spaceId)
                Route.Marketplace -> MarketplaceScreen()
                Route.Moving -> MovingScreen()
                Route.Files -> FilesScreen()
                Route.Polls -> PollsScreen()
                is Route.PollDetail -> PollsScreen()
                Route.Profile -> ProfileScreen()
                Route.ChatThreads -> ChatThreadsScreen()
                is Route.Chat -> ChatThreadScreen(r.threadId)
            }
        }
    }
}
