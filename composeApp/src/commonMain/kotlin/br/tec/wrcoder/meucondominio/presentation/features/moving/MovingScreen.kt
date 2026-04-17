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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import br.tec.wrcoder.meucondominio.core.formatBr
import br.tec.wrcoder.meucondominio.domain.model.MovingRequest
import br.tec.wrcoder.meucondominio.domain.model.MovingStatus
import br.tec.wrcoder.meucondominio.presentation.common.AppTopBar
import br.tec.wrcoder.meucondominio.presentation.common.EmptyState
import br.tec.wrcoder.meucondominio.presentation.common.IconBadge
import br.tec.wrcoder.meucondominio.presentation.common.PillTone
import br.tec.wrcoder.meucondominio.presentation.common.StatusPill
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
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

    if (s.editor.visible && s.today != null) {
        MovingEditorDialog(
            editor = s.editor,
            today = s.today!!,
            onDate = vm::onDate,
            onTime = vm::onTime,
            onSubmit = vm::submit,
            onDismiss = vm::dismiss,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovingEditorDialog(
    editor: MovingEditor,
    today: LocalDate,
    onDate: (LocalDate) -> Unit,
    onTime: (Int, Int) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val dateLabel = editor.date?.formatBr() ?: "Selecionar data"
    val timeLabel = "${editor.hour.toString().padStart(2, '0')}:${editor.minute.toString().padStart(2, '0')}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Solicitar mudança") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Agende a mudança para uma data dentro dos próximos $MOVING_MAX_DAYS_AHEAD dias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showDate = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(dateLabel)
                }
                OutlinedButton(
                    onClick = { showTime = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(timeLabel)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSubmit) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (showDate) {
        val minMillis = today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val maxMillis = today.plus(DatePeriod(days = MOVING_MAX_DAYS_AHEAD))
            .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val initialMillis = (editor.date ?: today).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            initialDisplayedMonthMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis in minMillis..maxMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.fromEpochMilliseconds(millis)
                            .toLocalDateTime(TimeZone.UTC).date
                        onDate(picked)
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(
            initialHour = editor.hour,
            initialMinute = editor.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text("Selecionar hora") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    onTime(timeState.hour, timeState.minute)
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text("Cancelar") }
            },
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
                    "Agendada para ${item.scheduledFor.formatBr()}",
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
