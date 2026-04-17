package br.tec.wrcoder.meucondominio.presentation.features.packages

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import br.tec.wrcoder.meucondominio.core.toLocalDateTime
import br.tec.wrcoder.meucondominio.domain.model.PackageItem
import br.tec.wrcoder.meucondominio.domain.model.PackageStatus
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
import br.tec.wrcoder.meucondominio.presentation.common.IconBadge
import br.tec.wrcoder.meucondominio.presentation.common.PillTone
import br.tec.wrcoder.meucondominio.presentation.common.StatusPill
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PackagesScreen(vm: PackagesViewModel = koinViewModel(), navigator: AppNavigator = koinInject()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AppTopBar("Encomendas", onBack = { navigator.back() }) },
        floatingActionButton = {
            if (s.canRegister) {
                ExtendedFloatingActionButton(
                    onClick = vm::showRegister,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Registrar") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.items.isEmpty()) {
                EmptyState(
                    title = "Nenhuma encomenda",
                    description = "Não há encomendas registradas no momento.",
                    icon = Icons.Filled.Inventory2,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(s.items, key = { it.id }) { item ->
                        PackageCard(item, canRegister = s.canRegister, onMarkPicked = { vm.markPickedUp(item) })
                    }
                }
            }
        }
    }

    if (s.editor.visible) {
        var expanded by remember { mutableStateOf(false) }
        val selectedUnitLabel = s.units.firstOrNull { it.id == s.editor.unitId }?.identifier.orEmpty()
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Registrar encomenda") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedTextField(
                            value = selectedUnitLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unidade") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            s.units.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit.identifier) },
                                    onClick = {
                                        vm.onUnit(unit.id)
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        s.editor.description,
                        vm::onDescription,
                        label = { Text("Descrição") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        s.editor.carrier,
                        vm::onCarrier,
                        label = { Text("Transportadora (opcional)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
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
            title = { Text("Erro") },
            text = { Text(it) },
        )
    }
}

@Composable
private fun PackageCard(item: PackageItem, canRegister: Boolean, onMarkPicked: () -> Unit) {
    val isReceived = item.status == PackageStatus.RECEIVED
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp)) {
            IconBadge(
                icon = Icons.Filled.Inventory2,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                background = MaterialTheme.colorScheme.tertiaryContainer,
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
                    StatusPill(
                        text = if (isReceived) "Aguardando retirada" else "Retirada",
                        tone = if (isReceived) PillTone.Warning else PillTone.Success,
                    )
                }
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.carrier?.let {
                    Text(
                        "Transportadora: $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Recebida em ${item.receivedAt.toLocalDateTime().date}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canRegister && isReceived) {
                    Spacer(Modifier.size(8.dp))
                    TextButton(onClick = onMarkPicked) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Marcar como retirada")
                    }
                }
            }
        }
    }
}
