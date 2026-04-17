package br.tec.wrcoder.meucondominio.core

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

fun newCondoCode(): String {
    val chars = ('A'..'Z') + ('0'..'9')
    return (1..8).map { chars.random() }.joinToString("")
}
