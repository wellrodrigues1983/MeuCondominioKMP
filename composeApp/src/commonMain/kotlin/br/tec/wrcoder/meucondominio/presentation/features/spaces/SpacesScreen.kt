package br.tec.wrcoder.meucondominio.presentation.features.spaces

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.domain.model.CommonSpace
import br.tec.wrcoder.meucondominio.domain.model.Reservation
import br.tec.wrcoder.meucondominio.domain.model.ReservationStatus
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
import br.tec.wrcoder.meucondominio.presentation.common.SectionHeader
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SpacesScreen(vm: SpacesViewModel = koinViewModel(), navigator: AppNavigator = koinInject()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AppTopBar("Espaços comuns", onBack = { navigator.back() }) },
        floatingActionButton = {
            if (s.canManage) {
                ExtendedFloatingActionButton(onClick = vm::showCreate) { Text("+ Novo espaço") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.spaces.isEmpty()) {
                EmptyState("Sem espaços", "Nenhum espaço cadastrado.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(s.spaces, key = { it.id }) { space ->
                        SpaceCard(space, onClick = { vm.openDetail(space) })
                    }
                    if (s.myReservations.isNotEmpty()) {
                        item { SectionHeader("Minhas reservas") }
                        items(s.myReservations, key = { it.id }) { reservation ->
                            ReservationCard(reservation, onCancel = { vm.cancelReservation(reservation) })
                        }
                    }
                }
            }
        }
    }

    if (s.editor.visible) {
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Novo espaço") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(s.editor.name, onValueChange = { vm.update { copy(name = it) } }, label = { Text("Nome") })
                    OutlinedTextField(s.editor.description, onValueChange = { vm.update { copy(description = it) } }, label = { Text("Descrição") }, minLines = 2)
                    OutlinedTextField(s.editor.price, onValueChange = { vm.update { copy(price = it) } }, label = { Text("Valor (R$)") })
                    OutlinedTextField(s.editor.imageUrls, onValueChange = { vm.update { copy(imageUrls = it) } }, label = { Text("URLs de imagens (vírgula)") })
                }
            },
            confirmButton = { TextButton(onClick = vm::save) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Cancelar") } },
        )
    }
    s.error?.let {
        AlertDialog(
            onDismissRequest = vm::clearError,
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
            title = { Text("Aviso") },
            text = { Text(it) },
        )
    }
}

@Composable
private fun SpaceCard(space: CommonSpace, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(space.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(space.description, style = MaterialTheme.typography.bodyMedium)
            Text("R$ ${space.price}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ReservationCard(reservation: Reservation, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${reservation.spaceName} — ${reservation.date}", fontWeight = FontWeight.SemiBold)
                AssistChip(onClick = {}, label = { Text(reservation.status.name) })
            }
            if (reservation.status == ReservationStatus.CONFIRMED) {
                TextButton(onClick = onCancel) { Text("Cancelar reserva") }
            }
            reservation.cancellationReason?.let {
                Text("Motivo: $it", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
