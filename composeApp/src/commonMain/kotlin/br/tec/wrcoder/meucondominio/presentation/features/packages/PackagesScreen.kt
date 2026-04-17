package br.tec.wrcoder.meucondominio.presentation.features.packages

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.core.toLocalDateTime
import br.tec.wrcoder.meucondominio.domain.model.PackageItem
import br.tec.wrcoder.meucondominio.domain.model.PackageStatus
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
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
                ExtendedFloatingActionButton(onClick = vm::showRegister) { Text("+ Registrar") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.items.isEmpty()) {
                EmptyState("Nenhuma encomenda", "Não há encomendas registradas.")
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
                    OutlinedTextField(s.editor.description, vm::onDescription, label = { Text("Descrição") })
                    OutlinedTextField(s.editor.carrier, vm::onCarrier, label = { Text("Transportadora (opcional)") })
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
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Unidade ${item.unitIdentifier}", fontWeight = FontWeight.SemiBold)
                AssistChip(onClick = {}, label = { Text(if (item.status == PackageStatus.RECEIVED) "Recebida" else "Retirada") })
            }
            Text(item.description, style = MaterialTheme.typography.bodyMedium)
            item.carrier?.let { Text("Transportadora: $it", style = MaterialTheme.typography.labelSmall) }
            Text(
                "Recebida em ${item.receivedAt.toLocalDateTime().date}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canRegister && item.status == PackageStatus.RECEIVED) {
                TextButton(onClick = onMarkPicked) { Text("Marcar como retirada") }
            }
        }
    }
}
