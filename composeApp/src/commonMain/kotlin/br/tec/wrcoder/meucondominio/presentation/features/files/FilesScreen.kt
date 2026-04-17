package br.tec.wrcoder.meucondominio.presentation.features.files

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
import br.tec.wrcoder.meucondominio.domain.model.FileDoc
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FilesScreen(vm: FilesViewModel = koinViewModel(), navigator: AppNavigator = koinInject()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AppTopBar("Arquivos", onBack = { navigator.back() }) },
        floatingActionButton = {
            if (s.canManage) {
                ExtendedFloatingActionButton(onClick = vm::showUpload) { Text("+ Enviar PDF") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.files.isEmpty()) EmptyState("Sem arquivos", "Nenhum documento disponibilizado.")
            else LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(s.files, key = { it.id }) { file ->
                    FileCard(file, canManage = s.canManage, onDelete = { vm.delete(file) })
                }
            }
        }
    }

    if (s.editor.visible) {
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Enviar PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(s.editor.title, { v -> vm.update { copy(title = v) } }, label = { Text("Título") })
                    OutlinedTextField(s.editor.description, { v -> vm.update { copy(description = v) } }, label = { Text("Descrição (opcional)") })
                    OutlinedTextField(s.editor.fileName, { v -> vm.update { copy(fileName = v) } }, label = { Text("Nome do arquivo (.pdf)") })
                }
            },
            confirmButton = { TextButton(onClick = vm::submit) { Text("Enviar") } },
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
private fun FileCard(file: FileDoc, canManage: Boolean, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(file.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                file.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(file.fileUrl, style = MaterialTheme.typography.labelSmall)
            }
            if (canManage) {
                TextButton(onClick = onDelete) { Text("Excluir") }
            }
        }
    }
}
