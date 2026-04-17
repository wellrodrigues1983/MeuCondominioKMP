package br.tec.wrcoder.meucondominio.presentation.features.spaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.tec.wrcoder.meucondominio.domain.model.Reservation
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val WEEKDAY_LABELS = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")

@Composable
fun AvailabilityCalendar(
    today: LocalDate,
    monthCount: Int,
    reservationsByDate: Map<LocalDate, Reservation>,
    selectedDate: LocalDate?,
    onSelectAvailable: (LocalDate) -> Unit,
    onClickReserved: (Reservation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        repeat(monthCount) { offset ->
            val anchor = today.plus(DatePeriod(months = offset))
            MonthGrid(
                year = anchor.year,
                month = anchor.month,
                today = today,
                reservationsByDate = reservationsByDate,
                selectedDate = selectedDate,
                onSelectAvailable = onSelectAvailable,
                onClickReserved = onClickReserved,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    year: Int,
    month: Month,
    today: LocalDate,
    reservationsByDate: Map<LocalDate, Reservation>,
    selectedDate: LocalDate?,
    onSelectAvailable: (LocalDate) -> Unit,
    onClickReserved: (Reservation) -> Unit,
) {
    val firstOfMonth = LocalDate(year, month, 1)
    val leadingBlanks = (firstOfMonth.dayOfWeek.ordinal + 1) % 7
    val daysInMonth = firstOfMonth.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${month.ptLabel()} $year",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        val totalCells = leadingBlanks + daysInMonth
        val rowCount = (totalCells + 6) / 7
        repeat(rowCount) { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) { col ->
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = LocalDate(year, month, dayNumber)
                        val reservation = reservationsByDate[date]
                        DayCell(
                            day = dayNumber,
                            date = date,
                            today = today,
                            reservation = reservation,
                            isSelected = selectedDate == date,
                            onSelectAvailable = onSelectAvailable,
                            onClickReserved = onClickReserved,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    date: LocalDate,
    today: LocalDate,
    reservation: Reservation?,
    isSelected: Boolean,
    onSelectAvailable: (LocalDate) -> Unit,
    onClickReserved: (Reservation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPast = date < today
    val isReserved = reservation != null

    val containerColor: Color = when {
        isReserved -> MaterialTheme.colorScheme.errorContainer
        isSelected -> MaterialTheme.colorScheme.primary
        isPast -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor: Color = when {
        isReserved -> MaterialTheme.colorScheme.onErrorContainer
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    val clickable = when {
        reservation != null -> Modifier.clickable { onClickReserved(reservation) }
        isPast -> Modifier
        else -> Modifier.clickable { onSelectAvailable(date) }
    }

    val todayRing = if (date == today && !isSelected) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .then(todayRing)
            .then(clickable),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = if (isSelected || isReserved) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun CalendarLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendSwatch(
            color = MaterialTheme.colorScheme.surfaceVariant,
            label = "Disponível",
        )
        LegendSwatch(
            color = MaterialTheme.colorScheme.primary,
            label = "Selecionada",
        )
        LegendSwatch(
            color = MaterialTheme.colorScheme.errorContainer,
            label = "Reservada",
        )
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color,
            modifier = Modifier.size(12.dp),
        ) {}
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Month.ptLabel(): String = when (this) {
    Month.JANUARY -> "Janeiro"
    Month.FEBRUARY -> "Fevereiro"
    Month.MARCH -> "Março"
    Month.APRIL -> "Abril"
    Month.MAY -> "Maio"
    Month.JUNE -> "Junho"
    Month.JULY -> "Julho"
    Month.AUGUST -> "Agosto"
    Month.SEPTEMBER -> "Setembro"
    Month.OCTOBER -> "Outubro"
    Month.NOVEMBER -> "Novembro"
    Month.DECEMBER -> "Dezembro"
}
