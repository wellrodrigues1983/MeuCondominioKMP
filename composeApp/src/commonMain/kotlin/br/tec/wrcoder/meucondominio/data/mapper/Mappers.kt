package br.tec.wrcoder.meucondominio.data.mapper

import br.tec.wrcoder.meucondominio.data.remote.ApiJson
import br.tec.wrcoder.meucondominio.data.remote.dto.ChatMessageDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ChatThreadDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CommonSpaceDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CondoUnitDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CondominiumDto
import br.tec.wrcoder.meucondominio.data.remote.dto.FileDocDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ListingDto
import br.tec.wrcoder.meucondominio.data.remote.dto.MovingDto
import br.tec.wrcoder.meucondominio.data.remote.dto.NoticeDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PackageDescriptionDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PackageItemDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PollDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PollOptionDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ReservationDto
import br.tec.wrcoder.meucondominio.data.remote.dto.UserDto
import br.tec.wrcoder.meucondominio.domain.model.ChatMessage
import br.tec.wrcoder.meucondominio.domain.model.ChatThread
import br.tec.wrcoder.meucondominio.domain.model.CommonSpace
import br.tec.wrcoder.meucondominio.domain.model.CondoUnit
import br.tec.wrcoder.meucondominio.domain.model.Condominium
import br.tec.wrcoder.meucondominio.domain.model.FileDoc
import br.tec.wrcoder.meucondominio.domain.model.Listing
import br.tec.wrcoder.meucondominio.domain.model.ListingStatus
import br.tec.wrcoder.meucondominio.domain.model.MovingRequest
import br.tec.wrcoder.meucondominio.domain.model.MovingStatus
import br.tec.wrcoder.meucondominio.domain.model.Notice
import br.tec.wrcoder.meucondominio.domain.model.PackageDescription
import br.tec.wrcoder.meucondominio.domain.model.PackageItem
import br.tec.wrcoder.meucondominio.domain.model.PackageStatus
import br.tec.wrcoder.meucondominio.domain.model.Poll
import br.tec.wrcoder.meucondominio.domain.model.PollOption
import br.tec.wrcoder.meucondominio.domain.model.PollStatus
import br.tec.wrcoder.meucondominio.domain.model.Reservation
import br.tec.wrcoder.meucondominio.domain.model.ReservationStatus
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

fun Instant.toEpoch(): Long = toEpochMilliseconds()
fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)
fun String.parseInstant(): Instant = Instant.parse(this)
fun Instant.iso(): String = toString()

private val stringListSerializer = ListSerializer(String.serializer())

internal fun encodeStrings(list: List<String>): String = ApiJson.encodeToString(stringListSerializer, list)
internal fun decodeStrings(json: String): List<String> = try {
    ApiJson.decodeFromString(stringListSerializer, json)
} catch (_: Throwable) { emptyList() }

// --- User ---

fun UserDto.toDomain(): User = User(
    id = id,
    name = name,
    email = email,
    phone = phone,
    role = UserRole.valueOf(role),
    condominiumId = condominiumId,
    unitId = unitId,
    avatarUrl = avatarUrl,
    createdAt = createdAt.parseInstant(),
)

// --- Condominium / Unit ---

fun CondominiumDto.toDomain(): Condominium = Condominium(
    id = id, name = name, address = address, code = code, createdAt = createdAt.parseInstant(),
)

fun CondoUnitDto.toDomain(): CondoUnit = CondoUnit(
    id = id, condominiumId = condominiumId, identifier = identifier, block = block, ownerUserId = ownerUserId,
)

// --- Notice ---

fun NoticeDto.toDomain(): Notice = Notice(
    id = id, condominiumId = condominiumId, title = title, description = description,
    authorId = authorId, authorName = authorName,
    createdAt = createdAt.parseInstant(), updatedAt = updatedAt.parseInstant(),
)

// --- Space / Reservation ---

fun CommonSpaceDto.toDomain(): CommonSpace = CommonSpace(
    id = id, condominiumId = condominiumId, name = name, description = description, price = price,
    imageUrls = imageUrls, active = active,
)

fun ReservationDto.toDomain(): Reservation = Reservation(
    id = id, spaceId = spaceId, spaceName = spaceName, unitId = unitId, unitIdentifier = unitIdentifier,
    residentUserId = residentUserId, residentName = residentName,
    date = LocalDate.parse(date), status = ReservationStatus.valueOf(status),
    createdAt = createdAt.parseInstant(), cancelledAt = cancelledAt?.parseInstant(),
    cancellationReason = cancellationReason,
)

// --- Listing ---

fun ListingDto.toDomain(): Listing = Listing(
    id = id, condominiumId = condominiumId, authorUserId = authorUserId, authorName = authorName,
    unitIdentifier = unitIdentifier, title = title, description = description, price = price,
    imageUrls = imageUrls, status = ListingStatus.valueOf(status),
    createdAt = createdAt.parseInstant(), expiresAt = expiresAt.parseInstant(),
)

// --- Moving ---

fun MovingDto.toDomain(): MovingRequest = MovingRequest(
    id = id, condominiumId = condominiumId, unitId = unitId, unitIdentifier = unitIdentifier,
    residentUserId = residentUserId, residentName = residentName,
    scheduledFor = LocalDateTime.parse(scheduledFor), status = MovingStatus.valueOf(status),
    createdAt = createdAt.parseInstant(), decisionReason = decisionReason,
    decidedByUserId = decidedByUserId, decidedAt = decidedAt?.parseInstant(),
)

// --- File ---

fun FileDocDto.toDomain(): FileDoc = FileDoc(
    id = id, condominiumId = condominiumId, title = title, description = description,
    fileUrl = fileUrl, sizeBytes = sizeBytes, uploadedByUserId = uploadedByUserId,
    uploadedAt = uploadedAt.parseInstant(),
)

// --- Poll ---

fun PollOptionDto.toDomain(): PollOption = PollOption(id, text)
fun PollOption.toDto(): PollOptionDto = PollOptionDto(id, text)

fun PollDto.toDomain(): Poll = Poll(
    id = id, condominiumId = condominiumId, question = question,
    options = options.map { it.toDomain() },
    startsAt = startsAt.parseInstant(), endsAt = endsAt.parseInstant(),
    status = PollStatus.valueOf(status),
    createdByUserId = createdByUserId, createdAt = createdAt.parseInstant(),
)

// --- Package ---

fun PackageItemDto.toDomain(): PackageItem = PackageItem(
    id = id, condominiumId = condominiumId, unitId = unitId, unitIdentifier = unitIdentifier,
    description = description, carrier = carrier, status = PackageStatus.valueOf(status),
    receivedAt = receivedAt.parseInstant(), pickedUpAt = pickedUpAt?.parseInstant(),
    registeredByUserId = registeredByUserId,
)

fun PackageDescriptionDto.toDomain(): PackageDescription =
    PackageDescription(id = id, condominiumId = condominiumId, text = text)

// --- Chat ---

fun ChatThreadDto.toDomain(): ChatThread = ChatThread(
    id = id, condominiumId = condominiumId, title = title, participantUserIds = participantUserIds,
    lastMessagePreview = lastMessagePreview, lastMessageAt = lastMessageAt?.parseInstant(),
)

fun ChatMessageDto.toDomain(): ChatMessage = ChatMessage(
    id = id, threadId = threadId, senderUserId = senderUserId, senderName = senderName,
    text = text, sentAt = sentAt.parseInstant(),
)
