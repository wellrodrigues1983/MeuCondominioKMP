package br.tec.wrcoder.meucondominio.presentation.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import br.tec.wrcoder.meucondominio.presentation.common.StatusPill
import br.tec.wrcoder.meucondominio.presentation.common.PillTone
import br.tec.wrcoder.meucondominio.presentation.navigation.Route
import br.tec.wrcoder.meucondominio.presentation.theme.AppTheme
import kotlinx.datetime.Instant
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(vm: HomeViewModel = koinViewModel()) {
    val user by vm.user.collectAsStateWithLifecycle()
    HomeScreenContent(
        user = user,
        features = vm.featuresFor(user),
        onLogout = vm::logout,
        onFeatureClick = vm::go,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreenContent(
    user: User?,
    features: List<HomeFeature>,
    onLogout: () -> Unit,
    onFeatureClick: (Route) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Meu Condomínio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sair")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            user?.let { GreetingCard(name = it.name, role = it.role) }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(features) { feature ->
                    val meta = featureMeta(feature.route)
                    FeatureTile(
                        title = feature.title,
                        icon = meta.icon,
                        tintBackground = meta.background(),
                        tintForeground = meta.foreground(),
                        onClick = { onFeatureClick(feature.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GreetingCard(name: String, role: UserRole) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Olá, ${name.substringBefore(' ')}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(4.dp))
                StatusPill(
                    text = roleLabel(role),
                    tone = when (role) {
                        UserRole.ADMIN -> PillTone.Warning
                        UserRole.SUPERVISOR -> PillTone.Info
                        UserRole.RESIDENT -> PillTone.Success
                    },
                )
            }
        }
    }
}

@Composable
private fun FeatureTile(
    title: String,
    icon: ImageVector,
    tintBackground: Color,
    tintForeground: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tintBackground,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = tintForeground,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private data class FeatureMeta(
    val icon: ImageVector,
    val background: @Composable () -> Color,
    val foreground: @Composable () -> Color,
)

private fun featureMeta(route: Route): FeatureMeta = when (route) {
    Route.Notices -> FeatureMeta(
        Icons.Filled.Campaign,
        { MaterialTheme.colorScheme.primaryContainer },
        { MaterialTheme.colorScheme.onPrimaryContainer },
    )
    Route.Packages -> FeatureMeta(
        Icons.Filled.Inventory2,
        { MaterialTheme.colorScheme.tertiaryContainer },
        { MaterialTheme.colorScheme.onTertiaryContainer },
    )
    Route.Spaces -> FeatureMeta(
        Icons.Filled.Deck,
        { MaterialTheme.colorScheme.secondaryContainer },
        { MaterialTheme.colorScheme.onSecondaryContainer },
    )
    Route.Marketplace -> FeatureMeta(
        Icons.Filled.Storefront,
        { MaterialTheme.colorScheme.tertiaryContainer },
        { MaterialTheme.colorScheme.onTertiaryContainer },
    )
    Route.Moving -> FeatureMeta(
        Icons.Filled.LocalShipping,
        { MaterialTheme.colorScheme.primaryContainer },
        { MaterialTheme.colorScheme.onPrimaryContainer },
    )
    Route.Files -> FeatureMeta(
        Icons.Filled.Description,
        { MaterialTheme.colorScheme.surfaceVariant },
        { MaterialTheme.colorScheme.onSurfaceVariant },
    )
    Route.Polls -> FeatureMeta(
        Icons.Filled.HowToVote,
        { MaterialTheme.colorScheme.secondaryContainer },
        { MaterialTheme.colorScheme.onSecondaryContainer },
    )
    Route.ChatThreads -> FeatureMeta(
        Icons.AutoMirrored.Filled.Chat,
        { MaterialTheme.colorScheme.primaryContainer },
        { MaterialTheme.colorScheme.onPrimaryContainer },
    )
    Route.Profile -> FeatureMeta(
        Icons.Filled.Person,
        { MaterialTheme.colorScheme.surfaceVariant },
        { MaterialTheme.colorScheme.onSurfaceVariant },
    )
    else -> FeatureMeta(
        Icons.Filled.Apartment,
        { MaterialTheme.colorScheme.surfaceVariant },
        { MaterialTheme.colorScheme.onSurfaceVariant },
    )
}

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.ADMIN -> "Administrador"
    UserRole.SUPERVISOR -> "Supervisor"
    UserRole.RESIDENT -> "Morador"
}

@Preview
@Composable
private fun HomeScreenPreview() {
    AppTheme {
        HomeScreenContent(
            user = User(
                id = "1",
                name = "Ana Admin",
                email = "admin@demo.com",
                role = UserRole.ADMIN,
                condominiumId = "condo-123",
                createdAt = Instant.fromEpochMilliseconds(0),
            ),
            features = listOf(
                HomeFeature("Avisos", Route.Notices),
                HomeFeature("Encomendas", Route.Packages),
                HomeFeature("Espaços", Route.Spaces),
                HomeFeature("Anúncios", Route.Marketplace),
                HomeFeature("Mudanças", Route.Moving),
                HomeFeature("Arquivos", Route.Files),
            ),
            onLogout = {},
            onFeatureClick = {},
        )
    }
}
