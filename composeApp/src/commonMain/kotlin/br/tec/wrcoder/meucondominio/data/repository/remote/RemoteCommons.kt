package br.tec.wrcoder.meucondominio.data.repository.remote

import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.data.remote.dto.ErrorDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ErrorEnvelopeDto
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException

suspend fun <T> runRemote(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (ce: CancellationException) {
    throw ce
} catch (e: ResponseException) {
    AppResult.Failure(mapHttpError(e))
} catch (e: HttpRequestTimeoutException) {
    AppResult.Failure(AppError.Network("Tempo de resposta esgotado. Tente novamente."))
} catch (e: ContentConvertException) {
    AppResult.Failure(AppError.Unknown("Resposta inesperada do servidor. Tente novamente."))
} catch (e: Throwable) {
    AppResult.Failure(AppError.Network("Sem conexão com o servidor. Verifique sua internet e tente novamente."))
}

private suspend fun mapHttpError(e: ResponseException): AppError {
    val parsed = runCatching { e.response.body<ErrorEnvelopeDto>().error }.getOrNull()
    val friendly = parsed?.let(::buildFriendlyMessage)
    return when (e.response.status) {
        HttpStatusCode.Unauthorized ->
            AppError.Unauthorized(friendly ?: "Sua sessão expirou. Entre novamente.")

        HttpStatusCode.Forbidden ->
            AppError.Forbidden(friendly ?: "Você não tem permissão para esta ação.")

        HttpStatusCode.NotFound ->
            AppError.NotFound(friendly ?: "Não encontramos o que você procurava.")

        HttpStatusCode.Conflict ->
            AppError.Validation(friendly ?: "Já existe um registro com esses dados.")

        HttpStatusCode.UnprocessableEntity, HttpStatusCode.BadRequest ->
            AppError.Validation(friendly ?: "Revise os dados e tente novamente.")

        HttpStatusCode.TooManyRequests ->
            AppError.Validation(
                friendly ?: "Muitas tentativas. Aguarde um momento e tente de novo."
            )

        HttpStatusCode.RequestTimeout ->
            AppError.Network("Tempo de resposta esgotado. Tente novamente.")

        HttpStatusCode.UnsupportedMediaType ->
            AppError.Validation(friendly ?: "Formato de arquivo não suportado.")

        HttpStatusCode.PayloadTooLarge ->
            AppError.Validation(friendly ?: "Arquivo muito grande.")

        else -> when (e.response.status.value) {
            in 500..599 -> AppError.Network(
                friendly ?: "Servidor indisponível. Tente novamente em instantes."
            )

            else -> AppError.Unknown(friendly ?: "Não foi possível completar a operação.")
        }
    }
}

private fun buildFriendlyMessage(err: ErrorDto): String? {
    val fieldMessages = err.details?.fields?.values
        ?.mapNotNull { raw -> raw.takeIf { it.isNotBlank() }?.let(::sanitizeFieldMessage) }
        ?.distinct()
        .orEmpty()
    if (fieldMessages.isNotEmpty()) {
        return fieldMessages.joinToString(". ") { it.replaceFirstChar { c -> c.uppercase() } } + "."
    }
    return err.message.takeIf { it.isNotBlank() }
}

private fun sanitizeFieldMessage(msg: String): String = msg
    .replace(Regex("tamanho deve ser entre (\\d+) e 2147483647"), "mínimo $1 caracteres")
    .replace(Regex("tamanho deve ser entre 0 e (\\d+)"), "máximo $1 caracteres")
    .replace(Regex("tamanho deve ser entre (\\d+) e (\\d+)"), "entre $1 e $2 caracteres")
    .replace("não deve ser nulo", "obrigatório")
    .replace("não deve estar em branco", "obrigatório")
    .replace("não deve estar vazio", "obrigatório")
    .replace("deve ser um endereço de e-mail bem formado", "e-mail inválido")
