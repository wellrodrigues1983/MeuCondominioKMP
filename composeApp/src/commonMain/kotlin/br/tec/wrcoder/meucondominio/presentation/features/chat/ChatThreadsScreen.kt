package br.tec.wrcoder.meucondominio.presentation.features.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.SectionHeader
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatThreadsScreen(vm: ChatThreadsViewModel = koinViewModel(), navigator: AppNavigator = koinInject()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Chat", onBack = { navigator.back() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (s.threads.isNotEmpty()) {
                    item { SectionHeader("Conversas") }
                    items(s.threads, key = { it.id }) { thread ->
                        Card(
                            Modifier.fillMaxWidth().clickable { vm.open(thread) },
                            elevation = CardDefaults.cardElevation(1.dp),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(thread.title, fontWeight = FontWeight.SemiBold)
                                thread.lastMessagePreview?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                item { SectionHeader("Contatos do condomínio") }
                items(s.contacts, key = { it.id }) { contact ->
                    Card(
                        Modifier.fillMaxWidth().clickable { vm.openOrCreate(contact) },
                        elevation = CardDefaults.cardElevation(1.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(contact.name, fontWeight = FontWeight.SemiBold)
                            Text(contact.role.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
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
