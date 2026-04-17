package br.tec.wrcoder.meucondominio.presentation.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ProfileScreen(vm: ProfileViewModel = koinViewModel(), navigator: AppNavigator = koinInject()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AppTopBar("Perfil", onBack = { navigator.back() }) },
        floatingActionButton = {
            if (s.canManageMembers) {
                ExtendedFloatingActionButton(onClick = vm::showAddMember) { Text("+ Adicionar membro") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            s.user?.let {
                Text(it.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(it.email, style = MaterialTheme.typography.bodyMedium)
                Text("Perfil: ${it.role.name}", style = MaterialTheme.typography.labelLarge)
                s.unit?.let { u ->
                    Text("Unidade: ${u.identifier}${u.block?.let { b -> " · bloco $b" } ?: ""}")
                }
            }
            Spacer(Modifier.height(16.dp))
            if (s.canManageMembers) {
                Text("Membros da unidade", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(s.members, key = { it.id }) { member ->
                        Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(member.name, fontWeight = FontWeight.SemiBold)
                                Text(member.email, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (s.editor.visible) {
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Adicionar membro da unidade") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(s.editor.name, { v -> vm.updateEditor { copy(name = v) } }, label = { Text("Nome") })
                    OutlinedTextField(s.editor.email, { v -> vm.updateEditor { copy(email = v) } }, label = { Text("E-mail") })
                    OutlinedTextField(
                        s.editor.password,
                        { v -> vm.updateEditor { copy(password = v) } },
                        label = { Text("Senha") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(s.editor.phone, { v -> vm.updateEditor { copy(phone = v) } }, label = { Text("Telefone (opcional)") })
                }
            },
            confirmButton = { TextButton(onClick = vm::save) { Text("Criar") } },
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
