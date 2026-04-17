package br.tec.wrcoder.meucondominio.presentation.features.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.domain.model.Reservation
import br.tec.wrcoder.meucondominio.domain.model.ReservationStatus
import br.tec.wrcoder.meucondominio.domain.repository.SpaceRepository
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.IconBadge
import br.tec.wrcoder.meucondominio.presentation.common.PillTone
import br.tec.wrcoder.meucondominio.presentation.common.SectionHeader
import br.tec.wrcoder.meucondominio.presentation.common.StatusPill
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import kotlinx.datetime.LocalDate
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SpaceDetailScreen(
    spaceId: String,
    spacesViewModel: SpacesViewModel = koinViewModel(),
    spaceRepository: SpaceRepository = koinInject(),
    navigator: AppNavigator = koinInject(),
) {
    val uiState by spacesViewModel.state.collectAsStateWithLifecycle()
    val space = uiState.spaces.firstOrNull { it.id == spaceId }

    var reservations by remember(spaceId) { mutableStateOf<List<Reservation>>(emptyList()) }
    LaunchedEffect(spaceId) {
        spaceRepository.observeReservations(spaceId).collect { reservations = it }
    }

    var pickedDate by remember { mutableStateOf("") }

    Scaffold(topBar = { AppTopBar(space?.name ?: "Espaço", onBack = { navigator.back() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (space != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(
                            icon = Icons.Filled.Deck,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            background = MaterialTheme.colorScheme.surface,
                            size = 52.dp,
                            iconSize = 26.dp,
                        )
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                space.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                "R$ ${space.price}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                Spacer(Modifier.size(16.dp))
                Text(
                    space.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (space.imageUrls.isNotEmpty()) {
                    Spacer(Modifier.size(8.dp))
                    space.imageUrls.forEach { url ->
                        Text(
                            url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.size(20.dp))
                SectionHeader("Nova reserva")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pickedDate,
                        onValueChange = { pickedDate = it },
                        label = { Text("Data (AAAA-MM-DD)") },
                        leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(
                        onClick = {
                            val date = runCatching { LocalDate.parse(pickedDate) }.getOrNull()
                            if (date != null) spacesViewModel.reserve(space, date)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(56.dp),
                    ) { Text("Reservar") }
                }

                Spacer(Modifier.size(20.dp))
                SectionHeader("Disponibilidade")
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(
                        reservations.filter { it.status == ReservationStatus.CONFIRMED },
                        key = { it.id },
                    ) { r ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        r.date.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                StatusPill(text = "Unidade ${r.unitIdentifier}", tone = PillTone.Info)
                            }
                        }
                    }
                }
            }
        }
    }
}
