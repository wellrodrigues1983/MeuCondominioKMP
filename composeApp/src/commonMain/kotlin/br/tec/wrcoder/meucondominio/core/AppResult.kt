package br.tec.wrcoder.meucondominio.core

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed class AppError(open val message: String, open val cause: Throwable? = null) {
    data class Network(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
    data class Unauthorized(override val message: String = "Sessão expirada") : AppError(message)
    data class Forbidden(override val message: String = "Você não tem permissão para esta ação") : AppError(message)
    data class NotFound(override val message: String) : AppError(message)
    data class Validation(override val message: String) : AppError(message)
    data class Unknown(override val message: String, override val cause: Throwable? = null) : AppError(message, cause)
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

fun <T> T.asSuccess(): AppResult<T> = AppResult.Success(this)
fun AppError.asFailure(): AppResult<Nothing> = AppResult.Failure(this)
