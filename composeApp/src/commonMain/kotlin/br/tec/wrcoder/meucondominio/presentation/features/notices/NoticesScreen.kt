package br.tec.wrcoder.meucondominio.presentation.features.notices

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
import br.tec.wrcoder.meucondominio.core.toLocalDateTime
import br.tec.wrcoder.meucondominio.domain.model.Notice
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NoticesScreen(vm: NoticesViewModel = koinViewModel(), navigator: AppNavigator = koinInject()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AppTopBar("Avisos", onBack = { navigator.back() }) },
        floatingActionButton = {
            if (s.canManage) {
                ExtendedFloatingActionButton(onClick = vm::showCreate) { Text("+ Novo aviso") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.notices.isEmpty()) {
                EmptyState("Nenhum aviso", "Ainda não há avisos publicados.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(s.notices, key = { it.id }) { notice ->
                        NoticeCard(
                            notice = notice,
                            canManage = s.canManage,
                            onEdit = { vm.showEdit(notice) },
                            onDelete = { vm.delete(notice) },
                        )
                    }
                }
            }
        }
    }

    if (s.editor.visible) {
        AlertDialog(
            onDismissRequest = vm::dismissEditor,
            title = { Text(if (s.editor.editingId == null) "Novo aviso" else "Editar aviso") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(s.editor.title, onValueChange = vm::onTitle, label = { Text("Título") })
                    OutlinedTextField(
                        s.editor.description,
                        onValueChange = vm::onDescription,
                        label = { Text("Descrição") },
                        minLines = 3,
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::save) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = vm::dismissEditor) { Text("Cancelar") } },
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
private fun NoticeCard(notice: Notice, canManage: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(notice.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(notice.description, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${notice.authorName} · ${notice.createdAt.toLocalDateTime().date}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canManage) {
                    Row {
                        TextButton(onClick = onEdit) { Text("Editar") }
                        TextButton(onClick = onDelete) { Text("Excluir") }
                    }
                }
            }
        }
    }
}
