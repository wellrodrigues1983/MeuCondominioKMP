package br.tec.wrcoder.meucondominio.presentation.features.spaces

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
                Text(space.description, style = MaterialTheme.typography.bodyMedium)
                Text("Valor: R$ ${space.price}", style = MaterialTheme.typography.titleSmall)
                space.imageUrls.forEach { url ->
                    Text(url, style = MaterialTheme.typography.labelSmall)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    OutlinedTextField(
                        value = pickedDate,
                        onValueChange = { pickedDate = it },
                        label = { Text("Data (AAAA-MM-DD)") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val date = runCatching { LocalDate.parse(pickedDate) }.getOrNull()
                            if (date != null) spacesViewModel.reserve(space, date)
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("Reservar") }
                }
                Text(
                    "Disponibilidade",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    items(
                        reservations.filter { it.status == ReservationStatus.CONFIRMED },
                        key = { it.id },
                    ) { r ->
                        Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(r.date.toString())
                                AssistChip(onClick = {}, label = { Text("Unidade ${r.unitIdentifier}") })
                            }
                        }
                    }
                }
            }
        }
    }
}
