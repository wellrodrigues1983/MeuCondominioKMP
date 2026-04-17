package br.tec.wrcoder.meucondominio.presentation.features.polls

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.tec.wrcoder.meucondominio.domain.model.Poll
import br.tec.wrcoder.meucondominio.domain.model.PollStatus
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
import br.tec.wrcoder.meucondominio.presentation.common.IconBadge
import br.tec.wrcoder.meucondominio.presentation.common.PillTone
import br.tec.wrcoder.meucondominio.presentation.common.StatusPill
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
                ExtendedFloatingActionButton(
                    onClick = vm::showCreate,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Nova enquete") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (s.polls.isEmpty()) {
                EmptyState(
                    title = "Sem enquetes",
                    description = "Nenhuma votação em andamento.",
                    icon = Icons.Filled.HowToVote,
                )
            } else {
                LazyColumn(
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
    }

    if (s.editor.visible) {
        AlertDialog(
            onDismissRequest = vm::dismiss,
            title = { Text("Nova enquete") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        s.editor.question,
                        { v -> vm.update { copy(question = v) } },
                        label = { Text("Pergunta") },
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        s.editor.options,
                        { v -> vm.update { copy(options = v) } },
                        label = { Text("Opções (uma por linha)") },
                        minLines = 3,
                        shape = RoundedCornerShape(12.dp),
                    )
                    OutlinedTextField(
                        s.editor.durationDays,
                        { v -> vm.update { copy(durationDays = v) } },
                        label = { Text("Duração (dias)") },
                        shape = RoundedCornerShape(12.dp),
                    )
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
    val (statusLabel, statusTone) = when (poll.status) {
        PollStatus.SCHEDULED -> "Agendada" to PillTone.Info
        PollStatus.OPEN -> "Aberta" to PillTone.Success
        PollStatus.CLOSED -> "Encerrada" to PillTone.Neutral
        PollStatus.CANCELLED -> "Cancelada" to PillTone.Danger
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(
                    icon = Icons.Filled.HowToVote,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    background = MaterialTheme.colorScheme.secondaryContainer,
                    size = 44.dp,
                    iconSize = 22.dp,
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            poll.question,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        StatusPill(text = statusLabel, tone = statusTone)
                    }
                    if (hasVoted) {
                        Spacer(Modifier.size(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                "Você já votou",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            poll.options.forEach { option ->
                val count = totals[option.id] ?: 0
                val fraction = if (totalVotes == 0) 0f else count.toFloat() / totalVotes
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (canVote) {
                            Button(
                                onClick = { onVote(option.id) },
                                shape = RoundedCornerShape(100),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Text("Votar", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (showResults) {
                            val pct = if (totalVotes == 0) 0 else (fraction * 100).toInt()
                            Text(
                                "$count · $pct%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (showResults) {
                        Spacer(Modifier.size(4.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (canManage && poll.status == PollStatus.OPEN) {
                Spacer(Modifier.size(4.dp))
                TextButton(onClick = onCancel) { Text("Cancelar enquete") }
            }
        }
    }
}
