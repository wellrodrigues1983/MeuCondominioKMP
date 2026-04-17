package br.tec.wrcoder.meucondominio.presentation.features.moving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.domain.model.MovingRequest
import br.tec.wrcoder.meucondominio.domain.model.MovingStatus
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
import br.tec.wrcoder.meucondominio.presentation.common.IconBadge
import br.tec.wrcoder.meucondominio.presentation.common.PillTone
import br.tec.wrcoder.meucondominio.presentation.common.StatusPill
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MovingScreen(vm: MovingViewModel = koinViewModel(), navigator: AppNavigator = koinInject()) {
    val s by vm.state.collectAsStateWithLifecycle()
    var rejecting by remember { mutableStateOf<MovingRequest?>(null) }
    var rejectionReason by remember { mutableStateOf("") }

    Scaffold(
        topBar = { AppTopBar("Mudanças", onBack = { navigator.back() }) },
        floatingActionButton = {
            if (s.canCreate) {
                ExtendedFloatingActionButton(
                    onClick = vm::showCreate,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Solicitar") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.items.isEmpty()) {
                EmptyState(
                    title = "Sem solicitações",
                    description = "Nenhuma mudança agendada por enquanto.",
                    icon = Icons.Filled.LocalShipping,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(s.items, key = { it.id }) { item ->
                        MovingCard(
                            item = item,
                            canDecide = s.canDecide,
                            onApprove = { vm.approve(item) },
                            onReject = {
                                rejecting = item
                                rejectionReason = ""
                            },
                        )
                    }
                }
            }
        }
    }

    if (s.editor.visible) {
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Solicitar mudança") },
            text = {
                OutlinedTextField(
                    s.editor.dateText,
                    onValueChange = vm::onDate,
                    label = { Text("Data e hora (AAAA-MM-DDTHH:MM)") },
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = { TextButton(onClick = vm::submit) { Text("Enviar") } },
            dismissButton = { TextButton(onClick = vm::dismiss) { Text("Cancelar") } },
        )
    }

    rejecting?.let { req ->
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text("Rejeitar solicitação") },
            text = {
                OutlinedTextField(
                    rejectionReason,
                    onValueChange = { rejectionReason = it },
                    label = { Text("Justificativa") },
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.reject(req, rejectionReason)
                    rejecting = null
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("Cancelar") } },
        )
    }

    s.error?.let {
        AlertDialog(
            onDismissRequest = vm::clearError,
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
            title = { Text("Erro") },
            text = { Text(it) },
        )
    }
}

@Composable
private fun MovingCard(
    item: MovingRequest,
    canDecide: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val (statusLabel, statusTone) = when (item.status) {
        MovingStatus.PENDING -> "Pendente" to PillTone.Warning
        MovingStatus.APPROVED -> "Aprovada" to PillTone.Success
        MovingStatus.REJECTED -> "Rejeitada" to PillTone.Danger
        MovingStatus.CANCELLED -> "Cancelada" to PillTone.Neutral
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp)) {
            IconBadge(
                icon = Icons.Filled.LocalShipping,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                background = MaterialTheme.colorScheme.primaryContainer,
                size = 44.dp,
                iconSize = 22.dp,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Unidade ${item.unitIdentifier}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    StatusPill(text = statusLabel, tone = statusTone)
                }
                Text(
                    "Agendada para ${item.scheduledFor}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.decisionReason?.let {
                    Text(
                        "Motivo: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canDecide && item.status == MovingStatus.PENDING) {
                    Spacer(Modifier.size(4.dp))
                    Row {
                        TextButton(onClick = onApprove) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("Aprovar")
                        }
                        TextButton(onClick = onReject) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("Rejeitar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
