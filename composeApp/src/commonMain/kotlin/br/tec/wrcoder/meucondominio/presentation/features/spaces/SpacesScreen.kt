package br.tec.wrcoder.meucondominio.presentation.features.spaces

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.core.formatBr
import br.tec.wrcoder.meucondominio.domain.model.CommonSpace
import br.tec.wrcoder.meucondominio.domain.model.Reservation
import br.tec.wrcoder.meucondominio.domain.model.ReservationStatus
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
import br.tec.wrcoder.meucondominio.presentation.common.IconBadge
import br.tec.wrcoder.meucondominio.presentation.common.ImagePickerField
import br.tec.wrcoder.meucondominio.presentation.common.MemoryImage
import br.tec.wrcoder.meucondominio.presentation.common.PillTone
import br.tec.wrcoder.meucondominio.presentation.common.SectionHeader
import br.tec.wrcoder.meucondominio.presentation.common.StatusPill
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
                ExtendedFloatingActionButton(
                    onClick = vm::showCreate,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Novo espaço") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.spaces.isEmpty()) {
                EmptyState(
                    title = "Sem espaços",
                    description = "Nenhum espaço comum cadastrado ainda.",
                    icon = Icons.Filled.Deck,
                )
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
                        item {
                            Column(Modifier.padding(horizontal = 4.dp)) {
                                SectionHeader("Minhas reservas")
                            }
                        }
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
                    OutlinedTextField(
                        s.editor.name,
                        onValueChange = { vm.update { copy(name = it) } },
                        label = { Text("Nome") },
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        s.editor.description,
                        onValueChange = { vm.update { copy(description = it) } },
                        label = { Text("Descrição") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        s.editor.price,
                        onValueChange = { vm.update { copy(price = it) } },
                        label = { Text("Valor (R$)") },
                        shape = RoundedCornerShape(12.dp),
                    )
                    ImagePickerField(
                        currentBytes = s.editor.imageBytes,
                        onPicked = { bytes -> vm.update { copy(imageBytes = bytes) } },
                        label = "Selecionar foto do espaço",
                        fallbackIcon = Icons.Filled.Deck,
                    )
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            val photoUrl = space.imageUrls.firstOrNull()
            if (photoUrl != null) {
                MemoryImage(
                    url = photoUrl,
                    fallbackIcon = Icons.Filled.Deck,
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
            }
            Row(Modifier.padding(16.dp)) {
                IconBadge(
                    icon = Icons.Filled.Deck,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    background = MaterialTheme.colorScheme.secondaryContainer,
                    size = 44.dp,
                    iconSize = 22.dp,
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        space.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        space.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "R$ ${space.price}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReservationCard(reservation: Reservation, onCancel: () -> Unit) {
    val tone = when (reservation.status) {
        ReservationStatus.CONFIRMED -> PillTone.Success
        ReservationStatus.CANCELLED_BY_RESIDENT,
        ReservationStatus.CANCELLED_BY_STAFF -> PillTone.Danger
    }
    val label = when (reservation.status) {
        ReservationStatus.CONFIRMED -> "Confirmada"
        ReservationStatus.CANCELLED_BY_RESIDENT -> "Cancelada"
        ReservationStatus.CANCELLED_BY_STAFF -> "Cancelada pela adm."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${reservation.spaceName} · ${reservation.date.formatBr()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(text = label, tone = tone)
            }
            if (reservation.status == ReservationStatus.CONFIRMED) {
                TextButton(onClick = onCancel) {
                    Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Cancelar reserva")
                }
            }
            reservation.cancellationReason?.let {
                Text(
                    "Motivo: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
