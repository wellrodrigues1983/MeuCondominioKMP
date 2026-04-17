package br.tec.wrcoder.meucondominio.core

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock as KClock

interface AppClock {
    fun now(): Instant
    fun timeZone(): TimeZone
}

class SystemAppClock : AppClock {
    override fun now(): Instant = KClock.System.now()
    override fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
}

fun Instant.toLocalDateTime(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
    toLocalDateTime(zone)

fun Instant.toLocalDate(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    toLocalDateTime(zone).date
