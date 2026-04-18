package br.tec.wrcoder.meucondominio.data.remote.dto

import kotlinx.serialization.Serializable

// --- Auth ---

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RegisterCondominiumRequestDto(
    val condominiumName: String,
    val address: String,
    val adminName: String,
    val adminEmail: String,
    val adminPassword: String,
)

@Serializable
data class JoinCondominiumRequestDto(
    val condoCode: String,
    val unitIdentifier: String,
    val name: String,
    val email: String,
    val password: String,
    val phone: String? = null,
)

@Serializable
data class CreateMemberRequestDto(
    val name: String,
    val email: String,
    val password: String,
    val phone: String? = null,
)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class AuthSessionDto(
    val accessToken: String,
    val refreshToken: String,
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
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

// --- Condominium & Unit ---

@Serializable
data class CondominiumDto(
    val id: String,
    val name: String,
    val address: String,
    val code: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class CondoUnitDto(
    val id: String,
    val condominiumId: String,
    val identifier: String,
    val block: String? = null,
    val ownerUserId: String? = null,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class CreateUnitRequestDto(val identifier: String, val block: String? = null)

// --- Generic sync envelope ---

@Serializable
data class PageDto<T>(
    val items: List<T>,
    val nextCursor: String? = null,
)

@Serializable
data class ErrorDto(
    val code: String = "",
    val message: String = "",
    val details: ErrorDetailsDto? = null,
)

@Serializable
data class ErrorDetailsDto(
    val fields: Map<String, String> = emptyMap(),
)

@Serializable
data class ErrorEnvelopeDto(val error: ErrorDto)

// --- Notice ---

@Serializable
data class NoticeDto(
    val id: String,
    val condominiumId: String,
    val title: String,
    val description: String,
    val authorId: String,
    val authorName: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class CreateNoticeRequestDto(val title: String, val description: String)

@Serializable
data class UpdateNoticeRequestDto(val title: String, val description: String)

// --- Space & Reservation ---

@Serializable
data class CommonSpaceDto(
    val id: String,
    val condominiumId: String,
    val name: String,
    val description: String,
    val price: Double,
    val imageUrls: List<String> = emptyList(),
    val active: Boolean = true,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class CreateSpaceRequestDto(
    val name: String,
    val description: String,
    val price: Double,
    val imageUrls: List<String> = emptyList(),
)

@Serializable
data class UpdateSpaceRequestDto(
    val name: String,
    val description: String,
    val price: Double,
    val imageUrls: List<String>,
    val active: Boolean,
)

@Serializable
data class ReservationDto(
    val id: String,
    val spaceId: String,
    val spaceName: String,
    val unitId: String,
    val unitIdentifier: String,
    val residentUserId: String,
    val residentName: String,
    val date: String,
    val status: String,
    val createdAt: String,
    val cancelledAt: String? = null,
    val cancellationReason: String? = null,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class CreateReservationRequestDto(val unitId: String, val date: String)

@Serializable
data class CancelReservationRequestDto(val reason: String? = null)

// --- Listing ---

@Serializable
data class ListingDto(
    val id: String,
    val condominiumId: String,
    val authorUserId: String,
    val authorName: String,
    val unitIdentifier: String,
    val title: String,
    val description: String,
    val price: Double? = null,
    val imageUrls: List<String> = emptyList(),
    val status: String,
    val createdAt: String,
    val expiresAt: String,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class CreateListingRequestDto(
    val title: String,
    val description: String,
    val price: Double? = null,
    val imageUrls: List<String> = emptyList(),
)

// --- Moving ---

@Serializable
data class MovingDto(
    val id: String,
    val condominiumId: String,
    val unitId: String,
    val unitIdentifier: String,
    val residentUserId: String,
    val residentName: String,
    val scheduledFor: String,
    val status: String,
    val createdAt: String,
    val decisionReason: String? = null,
    val decidedByUserId: String? = null,
    val decidedAt: String? = null,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class CreateMovingRequestDto(val unitId: String, val scheduledFor: String)

@Serializable
data class DecisionRequestDto(val reason: String? = null)

// --- File ---

@Serializable
data class FileDocDto(
    val id: String,
    val condominiumId: String,
    val title: String,
    val description: String? = null,
    val fileUrl: String,
    val sizeBytes: Long,
    val uploadedByUserId: String,
    val uploadedAt: String,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class UploadedFileRefDto(val id: String, val url: String, val fileName: String, val sizeBytes: Long)

@Serializable
data class UploadedImageRefDto(val id: String, val url: String)

// --- Poll ---

@Serializable
data class PollOptionDto(val id: String, val text: String)

@Serializable
data class PollDto(
    val id: String,
    val condominiumId: String,
    val question: String,
    val options: List<PollOptionDto>,
    val startsAt: String,
    val endsAt: String,
    val status: String,
    val createdByUserId: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class CreatePollRequestDto(
    val question: String,
    val options: List<String>,
    val startsAt: String,
    val endsAt: String,
)

@Serializable
data class VoteRequestDto(val optionId: String)

@Serializable
data class HasVotedResponseDto(val hasVoted: Boolean)

@Serializable
data class PollResultsDto(
    val pollId: String,
    val total: Int,
    val countsByOptionId: Map<String, Int>,
)

// --- Package ---

@Serializable
data class PackageItemDto(
    val id: String,
    val condominiumId: String,
    val unitId: String,
    val unitIdentifier: String,
    val description: String,
    val carrier: String? = null,
    val status: String,
    val receivedAt: String,
    val pickedUpAt: String? = null,
    val registeredByUserId: String,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class RegisterPackageRequestDto(
    val unitId: String,
    val description: String,
    val carrier: String? = null,
)

@Serializable
data class PackageDescriptionDto(
    val id: String,
    val condominiumId: String,
    val text: String,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class CreatePackageDescriptionRequestDto(val text: String)

// --- Chat ---

@Serializable
data class ChatThreadDto(
    val id: String,
    val condominiumId: String,
    val title: String,
    val participantUserIds: List<String>,
    val lastMessagePreview: String? = null,
    val lastMessageAt: String? = null,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class CreateChatThreadRequestDto(
    val title: String,
    val participantUserIds: List<String>,
)

@Serializable
data class ChatMessageDto(
    val id: String,
    val threadId: String,
    val senderUserId: String,
    val senderName: String,
    val text: String,
    val sentAt: String,
    val updatedAt: String,
    val version: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class SendMessageRequestDto(val text: String, val clientRefId: String? = null)

// --- Profile ---

@Serializable
data class UpdateAvatarRequestDto(val avatarUrl: String? = null)
