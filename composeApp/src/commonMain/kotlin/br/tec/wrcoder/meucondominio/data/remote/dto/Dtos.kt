package br.tec.wrcoder.meucondominio.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * DTOs for the remote boundary. Feature repositories map these to domain models.
 * In this scaffold only the auth/shapes that would most commonly be used are
 * included as examples; new features should follow the same pattern.
 */
@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class AuthSessionDto(
    val token: String,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val role: String,
    val condominiumId: String,
    val unitId: String? = null,
    val avatarUrl: String? = null,
    val createdAt: String,
)

@Serializable
data class ErrorDto(
    val code: String,
    val message: String,
)
