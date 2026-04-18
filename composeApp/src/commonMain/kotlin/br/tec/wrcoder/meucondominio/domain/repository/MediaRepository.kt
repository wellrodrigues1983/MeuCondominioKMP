package br.tec.wrcoder.meucondominio.domain.repository

import br.tec.wrcoder.meucondominio.core.AppResult

interface MediaRepository {
    suspend fun uploadImage(
        bytes: ByteArray,
        fileName: String = "image.jpg",
        mime: String = "image/jpeg",
    ): AppResult<String>

    suspend fun fetchImageBytes(url: String): AppResult<ByteArray>
}
