package br.tec.wrcoder.meucondominio.core

interface FileOpener {
    suspend fun openPdf(bytes: ByteArray, fileName: String): Boolean
}

expect fun createFileOpener(): FileOpener
