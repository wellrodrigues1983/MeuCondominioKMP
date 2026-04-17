package br.tec.wrcoder.meucondominio.presentation.features.chat

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.AvatarImage
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
import br.tec.wrcoder.meucondominio.presentation.common.PillTone
import br.tec.wrcoder.meucondominio.presentation.common.SectionHeader
import br.tec.wrcoder.meucondominio.presentation.common.StatusPill
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChatThreadsScreen(vm: ChatThreadsViewModel = koinViewModel(), navigator: AppNavigator = koinInject()) {
    val s by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Chat", onBack = { navigator.back() }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.threads.isEmpty() && s.contacts.isEmpty()) {
                EmptyState(
                    title = "Sem conversas",
                    description = "Inicie uma conversa pelos contatos do condomínio.",
                    icon = Icons.Filled.Chat,
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (s.threads.isNotEmpty()) {
                        item { SectionHeader("Grupo e conversas") }
                        items(s.threads, key = { it.id }) { thread ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { vm.open(thread) },
                                shape = RoundedCornerShape(14.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Avatar(thread.title, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.size(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            thread.title,
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        thread.lastMessagePreview?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (s.contacts.isNotEmpty()) {
                        item { SectionHeader("Supervisores — conversa privada") }
                    }
                    items(s.contacts, key = { it.id }) { contact ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { vm.openOrCreate(contact) },
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AvatarImage(
                                    name = contact.name,
                                    avatarUrl = contact.avatarUrl,
                                    background = MaterialTheme.colorScheme.secondaryContainer,
                                    foreground = MaterialTheme.colorScheme.onSecondaryContainer,
                                    size = 40.dp,
                                )
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        contact.name,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        roleLabel(contact.role),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                StatusPill(
                                    text = roleLabel(contact.role),
                                    tone = when (contact.role) {
                                        UserRole.ADMIN -> PillTone.Warning
                                        UserRole.SUPERVISOR -> PillTone.Info
                                        UserRole.RESIDENT -> PillTone.Success
                                    },
                                )
                            }
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

@Composable
private fun Avatar(name: String, bg: Color, fg: Color) {
    Surface(
        shape = CircleShape,
        color = bg,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                fontWeight = FontWeight.Bold,
                color = fg,
            )
        }
    }
}

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.ADMIN -> "Administrador"
    UserRole.SUPERVISOR -> "Supervisor"
    UserRole.RESIDENT -> "Morador"
}
