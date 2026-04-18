package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.BinaryStore
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.core.asSuccess
import br.tec.wrcoder.meucondominio.domain.repository.MediaRepository

class FakeMediaRepository(
    private val binaryStore: BinaryStore,
) : MediaRepository {

    override suspend fun uploadImage(
        bytes: ByteArray, fileName: String, mime: String,
    ): AppResult<String> = binaryStore.putImage(bytes).asSuccess()

    override suspend fun fetchImageBytes(url: String): AppResult<ByteArray> =
        binaryStore.get(url)?.asSuccess()
            ?: AppError.NotFound("Imagem indisponível").asFailure()
}
