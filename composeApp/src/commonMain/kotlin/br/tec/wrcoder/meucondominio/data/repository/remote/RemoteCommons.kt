package br.tec.wrcoder.meucondominio.data.repository.remote

import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

suspend fun <T> runRemote(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (ce: CancellationException) {
    throw ce
} catch (e: ClientRequestException) {
    val msg = e.message ?: "Erro"
    val err = when (e.response.status) {
        HttpStatusCode.Unauthorized -> AppError.Unauthorized()
        HttpStatusCode.Forbidden -> AppError.Forbidden()
        HttpStatusCode.NotFound -> AppError.NotFound(msg)
        HttpStatusCode.UnprocessableEntity, HttpStatusCode.BadRequest -> AppError.Validation(msg)
        else -> AppError.Unknown(msg, e)
    }
    AppResult.Failure(err)
} catch (e: ServerResponseException) {
    AppResult.Failure(AppError.Network(e.message ?: "Servidor indisponível", e))
} catch (e: ResponseException) {
    AppResult.Failure(AppError.Network(e.message ?: "Erro de rede", e))
} catch (e: Throwable) {
    AppResult.Failure(AppError.Network(e.message ?: "Falha de rede", e))
}
