package br.tec.wrcoder.meucondominio.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

fun LocalDate.formatBr(): String {
    val d = day.toString().padStart(2, '0')
    val m = (month.ordinal + 1).toString().padStart(2, '0')
    return "$d/$m/$year"
}

fun LocalDateTime.formatBr(): String {
    val h = hour.toString().padStart(2, '0')
    val min = minute.toString().padStart(2, '0')
    return "${date.formatBr()} $h:$min"
}
