package br.tec.wrcoder.meucondominio.domain.model

import kotlinx.datetime.Instant

enum class UserRole { ADMIN, SUPERVISOR, RESIDENT }

data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val role: UserRole,
    val condominiumId: String,
    val unitId: String? = null,
    val avatarUrl: String? = null,
    val createdAt: Instant,
)

data class AuthSession(
    val token: String,
    val user: User,
    val issuedAt: Instant,
)

data class LoginCredentials(val email: String, val password: String)

data class RegisterCondominiumInput(
    val condominiumName: String,
    val address: String,
    val adminName: String,
    val adminEmail: String,
    val adminPassword: String,
)

data class JoinCondominiumInput(
    val condoCode: String,
    val unitIdentifier: String,
    val name: String,
    val email: String,
    val password: String,
    val phone: String? = null,
)

data class CreateMemberInput(
    val unitId: String,
    val name: String,
    val email: String,
    val password: String,
    val phone: String? = null,
)
