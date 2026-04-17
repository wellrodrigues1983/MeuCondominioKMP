package br.tec.wrcoder.meucondominio

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
