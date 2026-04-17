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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.formatBr
import br.tec.wrcoder.meucondominio.core.toLocalDate
import br.tec.wrcoder.meucondominio.domain.model.Reservation
import br.tec.wrcoder.meucondominio.domain.model.ReservationStatus
import br.tec.wrcoder.meucondominio.domain.repository.SpaceRepository
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.IconBadge
import br.tec.wrcoder.meucondominio.presentation.common.MemoryImage
import br.tec.wrcoder.meucondominio.presentation.common.SectionHeader
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import kotlinx.datetime.LocalDate
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val MONTHS_TO_SHOW = 6

@Composable
fun SpaceDetailScreen(
    spaceId: String,
    spacesViewModel: SpacesViewModel = koinViewModel(),
    spaceRepository: SpaceRepository = koinInject(),
    navigator: AppNavigator = koinInject(),
    clock: AppClock = koinInject(),
) {
    val uiState by spacesViewModel.state.collectAsStateWithLifecycle()
    val space = uiState.spaces.firstOrNull { it.id == spaceId }

    var reservations by remember(spaceId) { mutableStateOf<List<Reservation>>(emptyList()) }
    LaunchedEffect(spaceId) {
        spaceRepository.observeReservations(spaceId).collect { reservations = it }
    }

    val today = remember(clock) { clock.now().toLocalDate(clock.timeZone()) }
    var selectedDate by remember(spaceId) { mutableStateOf<LocalDate?>(null) }
    var tappedReservation by remember(spaceId) { mutableStateOf<Reservation?>(null) }

    val reservationsByDate = remember(reservations) {
        reservations
            .filter { it.status == ReservationStatus.CONFIRMED }
            .associateBy { it.date }
    }

    Scaffold(topBar = { AppTopBar(space?.name ?: "Espaço", onBack = { navigator.back() }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
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

                space.imageUrls.firstOrNull()?.let { url ->
                    Spacer(Modifier.size(12.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MemoryImage(
                            url = url,
                            fallbackIcon = Icons.Filled.Deck,
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                        )
                    }
                }

                Spacer(Modifier.size(20.dp))
                SectionHeader("Nova reserva")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        selectedDate?.let { "Data selecionada: ${it.formatBr()}" }
                            ?: "Toque em uma data disponível no calendário",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            selectedDate?.let { date ->
                                spacesViewModel.reserve(space, date)
                                selectedDate = null
                            }
                        },
                        enabled = selectedDate != null,
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Reservar") }
                }

                Spacer(Modifier.size(20.dp))
                SectionHeader("Disponibilidade")
                CalendarLegend()
                Spacer(Modifier.size(12.dp))
                AvailabilityCalendar(
                    today = today,
                    monthCount = MONTHS_TO_SHOW,
                    reservationsByDate = reservationsByDate,
                    selectedDate = selectedDate,
                    onSelectAvailable = { selectedDate = it },
                    onClickReserved = { tappedReservation = it },
                )
            }
        }
    }

    tappedReservation?.let { r ->
        AlertDialog(
            onDismissRequest = { tappedReservation = null },
            title = { Text("Data reservada") },
            text = {
                Column {
                    Text(
                        r.date.formatBr(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Unidade ${r.unitIdentifier}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (r.residentName.isNotBlank()) {
                        Text(
                            "Morador: ${r.residentName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { tappedReservation = null }) { Text("OK") }
            },
        )
    }
}
