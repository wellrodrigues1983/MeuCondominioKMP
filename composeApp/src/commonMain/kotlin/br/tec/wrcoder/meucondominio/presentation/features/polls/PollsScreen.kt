package br.tec.wrcoder.meucondominio.presentation.features.polls

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.domain.model.Poll
import br.tec.wrcoder.meucondominio.domain.model.PollStatus
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PollsScreen(vm: PollsViewModel = koinViewModel(), navigator: AppNavigator = koinInject()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.polls) { vm.loadResultsAndVoted() }

    Scaffold(
        topBar = { AppTopBar("Enquetes", onBack = { navigator.back() }) },
        floatingActionButton = {
            if (s.canManage) {
                ExtendedFloatingActionButton(onClick = vm::showCreate) { Text("+ Nova enquete") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.polls.isEmpty()) EmptyState("Sem enquetes", "Nenhuma enquete no momento.")
            else LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(s.polls, key = { it.id }) { poll ->
                    val hasVoted = poll.id in s.votedPollIds
                    val showResults = hasVoted || poll.status == PollStatus.CLOSED || poll.status == PollStatus.CANCELLED
                    PollCard(
                        poll = poll,
                        hasVoted = hasVoted,
                        showResults = showResults,
                        totals = s.results[poll.id]?.countsByOptionId ?: emptyMap(),
                        totalVotes = s.results[poll.id]?.total ?: 0,
                        canVote = s.canVote && !hasVoted && poll.status == PollStatus.OPEN,
                        canManage = s.canManage,
                        onVote = { optId -> vm.vote(poll, optId) },
                        onCancel = { vm.cancel(poll) },
                    )
                }
            }
        }
    }

    if (s.editor.visible) {
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Nova enquete") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(s.editor.question, { v -> vm.update { copy(question = v) } }, label = { Text("Pergunta") })
                    OutlinedTextField(
                        s.editor.options,
                        { v -> vm.update { copy(options = v) } },
                        label = { Text("Opções (uma por linha)") },
                        minLines = 3,
                    )
                    OutlinedTextField(s.editor.durationDays, { v -> vm.update { copy(durationDays = v) } }, label = { Text("Duração (dias)") })
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
            title = { Text("Aviso") },
            text = { Text(it) },
        )
    }
}

@Composable
private fun PollCard(
    poll: Poll,
    hasVoted: Boolean,
    showResults: Boolean,
    totals: Map<String, Int>,
    totalVotes: Int,
    canVote: Boolean,
    canManage: Boolean,
    onVote: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(poll.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(poll.status.name) })
            }
            poll.options.forEach { option ->
                val count = totals[option.id] ?: 0
                val fraction = if (totalVotes == 0) 0f else count.toFloat() / totalVotes
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(option.text, Modifier.weight(1f))
                    if (canVote) TextButton(onClick = { onVote(option.id) }) { Text("Votar") }
                    if (showResults) Text("$count", style = MaterialTheme.typography.labelMedium)
                }
                if (showResults) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            if (hasVoted) Text("Você já votou.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            if (canManage && poll.status == PollStatus.OPEN) {
                TextButton(onClick = onCancel) { Text("Cancelar enquete") }
            }
        }
    }
}
