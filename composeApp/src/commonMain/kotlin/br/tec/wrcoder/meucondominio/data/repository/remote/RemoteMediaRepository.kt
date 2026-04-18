package br.tec.wrcoder.meucondominio.data.repository.remote

import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.BinaryStore
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.data.remote.UploadsApiService
import br.tec.wrcoder.meucondominio.domain.repository.MediaRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class RemoteMediaRepository(
    private val uploads: UploadsApiService,
    private val http: HttpClient,
    private val binaryStore: BinaryStore,
    private val network: NetworkMonitor,
) : MediaRepository {

    override suspend fun uploadImage(
        bytes: ByteArray, fileName: String, mime: String,
    ): AppResult<String> {
        if (!network.isOnline.value) {
            return AppResult.Failure(AppError.Network("Conecte à internet para enviar a imagem."))
        }
        return runRemote {
            val ref = uploads.uploadImage(bytes, fileName, mime)
            binaryStore.put(ref.url, bytes)
            ref.url
        }
    }

    override suspend fun fetchImageBytes(url: String): AppResult<ByteArray> {
        binaryStore.get(url)?.let { return AppResult.Success(it) }
        if (url.startsWith("memory://")) {
            return AppResult.Failure(AppError.NotFound("Imagem indisponível"))
        }
        if (!network.isOnline.value) {
            return AppResult.Failure(AppError.Network("Sem conexão"))
        }
        return runRemote {
            val bytes: ByteArray = http.get(url.trimStart('/')).body()
            binaryStore.put(url, bytes)
            bytes
        }
    }
}
