package br.tec.wrcoder.meucondominio.data.remote

import br.tec.wrcoder.meucondominio.data.remote.dto.AuthSessionDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CancelReservationRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ChatMessageDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ChatThreadDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CommonSpaceDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CondoUnitDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CondominiumDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateChatThreadRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateListingRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateMemberRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateMovingRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateNoticeRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreatePackageDescriptionRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreatePollRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateReservationRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateSpaceRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.CreateUnitRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.DecisionRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.FileDocDto
import br.tec.wrcoder.meucondominio.data.remote.dto.HasVotedResponseDto
import br.tec.wrcoder.meucondominio.data.remote.dto.JoinCondominiumRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ListingDto
import br.tec.wrcoder.meucondominio.data.remote.dto.LoginRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.MovingDto
import br.tec.wrcoder.meucondominio.data.remote.dto.NoticeDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PackageDescriptionDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PackageItemDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PageDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PollDto
import br.tec.wrcoder.meucondominio.data.remote.dto.PollResultsDto
import br.tec.wrcoder.meucondominio.data.remote.dto.RegisterCondominiumRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.RegisterPackageRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ReservationDto
import br.tec.wrcoder.meucondominio.data.remote.dto.SendMessageRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.UpdateAvatarRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.UpdateNoticeRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.UpdateSpaceRequestDto
import br.tec.wrcoder.meucondominio.data.remote.dto.UploadedFileRefDto
import br.tec.wrcoder.meucondominio.data.remote.dto.UploadedImageRefDto
import br.tec.wrcoder.meucondominio.data.remote.dto.UserDto
import br.tec.wrcoder.meucondominio.data.remote.dto.VoteRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

class AuthApiService(private val http: HttpClient) {
    suspend fun login(body: LoginRequestDto): AuthSessionDto = http.post("auth/login") { setBody(body) }.body()
    suspend fun registerCondominium(body: RegisterCondominiumRequestDto): AuthSessionDto =
        http.post("auth/register-condominium") { setBody(body) }.body()
    suspend fun joinCondominium(body: JoinCondominiumRequestDto): AuthSessionDto =
        http.post("auth/join-condominium") { setBody(body) }.body()
    suspend fun logout() { http.post("auth/logout") }
    suspend fun me(): UserDto = http.get("me").body()
    suspend fun updateAvatar(body: UpdateAvatarRequestDto): UserDto = http.patch("me/avatar") { setBody(body) }.body()
    suspend fun uploadAvatar(bytes: ByteArray, fileName: String, mime: String): UserDto =
        http.patch("me/avatar") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, mime)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    }
                )
            )
        }.body()
    suspend fun createUnitMember(unitId: String, body: CreateMemberRequestDto): UserDto =
        http.post("units/$unitId/members") { setBody(body) }.body()
    suspend fun listUnitMembers(unitId: String): List<UserDto> =
        http.get("units/$unitId/members").body()
}

class CondominiumApiService(private val http: HttpClient) {
    suspend fun byCode(code: String): CondominiumDto = http.get("condominiums/by-code/$code").body()
    suspend fun get(id: String): CondominiumDto = http.get("condominiums/$id").body()
    suspend fun listUnits(condominiumId: String): List<CondoUnitDto> =
        http.get("condominiums/$condominiumId/units").body<PageDto<CondoUnitDto>>().items
    suspend fun findUnitByIdentifier(condominiumId: String, identifier: String): CondoUnitDto =
        http.get("condominiums/$condominiumId/units/by-identifier/$identifier").body()
    suspend fun createUnit(condominiumId: String, body: CreateUnitRequestDto): CondoUnitDto =
        http.post("condominiums/$condominiumId/units") { setBody(body) }.body()
    suspend fun listMembers(condominiumId: String, role: String? = null): List<UserDto> =
        http.get("condominiums/$condominiumId/members") { role?.let { parameter("role", it) } }
            .body<PageDto<UserDto>>().items
}

class NoticesApiService(private val http: HttpClient) {
    suspend fun list(condominiumId: String, updatedSince: String? = null): List<NoticeDto> =
        http.get("condominiums/$condominiumId/notices") { updatedSince?.let { parameter("updatedSince", it) } }
            .body<PageDto<NoticeDto>>().items
    suspend fun create(condominiumId: String, body: CreateNoticeRequestDto): NoticeDto =
        http.post("condominiums/$condominiumId/notices") { setBody(body) }.body()
    suspend fun update(id: String, body: UpdateNoticeRequestDto): NoticeDto =
        http.patch("notices/$id") { setBody(body) }.body()
    suspend fun delete(id: String) { http.delete("notices/$id") }
}

class SpacesApiService(private val http: HttpClient) {
    suspend fun listSpaces(condominiumId: String, updatedSince: String? = null): List<CommonSpaceDto> =
        http.get("condominiums/$condominiumId/spaces") { updatedSince?.let { parameter("updatedSince", it) } }
            .body<PageDto<CommonSpaceDto>>().items
    suspend fun createSpace(condominiumId: String, body: CreateSpaceRequestDto): CommonSpaceDto =
        http.post("condominiums/$condominiumId/spaces") { setBody(body) }.body()
    suspend fun updateSpace(id: String, body: UpdateSpaceRequestDto): CommonSpaceDto =
        http.patch("spaces/$id") { setBody(body) }.body()
    suspend fun deleteSpace(id: String) { http.delete("spaces/$id") }
    suspend fun listReservationsBySpace(spaceId: String, updatedSince: String? = null): List<ReservationDto> =
        http.get("spaces/$spaceId/reservations") { updatedSince?.let { parameter("updatedSince", it) } }
            .body<PageDto<ReservationDto>>().items
    suspend fun listReservationsByUnit(unitId: String, updatedSince: String? = null): List<ReservationDto> =
        http.get("units/$unitId/reservations") { updatedSince?.let { parameter("updatedSince", it) } }
            .body<PageDto<ReservationDto>>().items
    suspend fun reserve(spaceId: String, body: CreateReservationRequestDto): ReservationDto =
        http.post("spaces/$spaceId/reservations") { setBody(body) }.body()
    suspend fun cancelByResident(reservationId: String): ReservationDto =
        http.post("reservations/$reservationId/cancel-by-resident").body()
    suspend fun cancelByStaff(reservationId: String, body: CancelReservationRequestDto): ReservationDto =
        http.post("reservations/$reservationId/cancel-by-staff") { setBody(body) }.body()
}

class ListingsApiService(private val http: HttpClient) {
    suspend fun list(condominiumId: String, updatedSince: String? = null): List<ListingDto> =
        http.get("condominiums/$condominiumId/listings") { updatedSince?.let { parameter("updatedSince", it) } }
            .body<PageDto<ListingDto>>().items
    suspend fun create(condominiumId: String, body: CreateListingRequestDto): ListingDto =
        http.post("condominiums/$condominiumId/listings") { setBody(body) }.body()
    suspend fun close(id: String): ListingDto = http.post("listings/$id/close").body()
    suspend fun renew(id: String): ListingDto = http.post("listings/$id/renew").body()
}

class MovingsApiService(private val http: HttpClient) {
    suspend fun listByCondominium(condominiumId: String, updatedSince: String? = null): List<MovingDto> =
        http.get("condominiums/$condominiumId/movings") { updatedSince?.let { parameter("updatedSince", it) } }
            .body<PageDto<MovingDto>>().items
    suspend fun listByUnit(unitId: String, updatedSince: String? = null): List<MovingDto> =
        http.get("units/$unitId/movings") { updatedSince?.let { parameter("updatedSince", it) } }
            .body<PageDto<MovingDto>>().items
    suspend fun create(condominiumId: String, body: CreateMovingRequestDto): MovingDto =
        http.post("condominiums/$condominiumId/movings") { setBody(body) }.body()
    suspend fun approve(id: String): MovingDto = http.post("movings/$id/approve").body()
    suspend fun reject(id: String, body: DecisionRequestDto): MovingDto =
        http.post("movings/$id/reject") { setBody(body) }.body()
    suspend fun cancelByStaff(id: String, body: DecisionRequestDto): MovingDto =
        http.post("movings/$id/cancel-by-staff") { setBody(body) }.body()
}

class FilesApiService(private val http: HttpClient) {
    suspend fun list(condominiumId: String, updatedSince: String? = null): List<FileDocDto> =
        http.get("condominiums/$condominiumId/files") { updatedSince?.let { parameter("updatedSince", it) } }
            .body<PageDto<FileDocDto>>().items
    suspend fun upload(
        condominiumId: String,
        title: String,
        description: String?,
        fileName: String,
        bytes: ByteArray,
    ): FileDocDto = http.post("condominiums/$condominiumId/files") {
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("title", title)
                    description?.let { append("description", it) }
                    append(
                        "file",
                        bytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        },
                    )
                }
            )
        )
    }.body()
    suspend fun delete(id: String) { http.delete("files/$id") }
    suspend fun download(fileUrl: String): ByteArray =
        http.get(fileUrl.trimStart('/')).body()
}

class PollsApiService(private val http: HttpClient) {
    suspend fun list(condominiumId: String, updatedSince: String? = null): List<PollDto> =
        http.get("condominiums/$condominiumId/polls") { updatedSince?.let { parameter("updatedSince", it) } }
            .body<PageDto<PollDto>>().items
    suspend fun create(condominiumId: String, body: CreatePollRequestDto): PollDto =
        http.post("condominiums/$condominiumId/polls") { setBody(body) }.body()
    suspend fun cancel(id: String): PollDto = http.post("polls/$id/cancel").body()
    suspend fun vote(id: String, body: VoteRequestDto) { http.post("polls/$id/vote") { setBody(body) } }
    suspend fun hasVoted(id: String): HasVotedResponseDto = http.get("polls/$id/has-voted").body()
    suspend fun results(id: String): PollResultsDto = http.get("polls/$id/results").body()
}

class PackagesApiService(private val http: HttpClient) {
    suspend fun listByCondominium(condominiumId: String, updatedSince: String? = null): List<PackageItemDto> =
        http.get("condominiums/$condominiumId/packages") { updatedSince?.let { parameter("updatedSince", it) } }
            .body<PageDto<PackageItemDto>>().items
    suspend fun listByUnit(unitId: String, updatedSince: String? = null): List<PackageItemDto> =
        http.get("units/$unitId/packages") { updatedSince?.let { parameter("updatedSince", it) } }
            .body<PageDto<PackageItemDto>>().items
    suspend fun register(condominiumId: String, body: RegisterPackageRequestDto): PackageItemDto =
        http.post("condominiums/$condominiumId/packages") { setBody(body) }.body()
    suspend fun markPickedUp(id: String): PackageItemDto = http.post("packages/$id/pickup").body()
    suspend fun listDescriptions(condominiumId: String): List<PackageDescriptionDto> =
        http.get("condominiums/$condominiumId/package-descriptions").body()
    suspend fun createDescription(condominiumId: String, body: CreatePackageDescriptionRequestDto): PackageDescriptionDto =
        http.post("condominiums/$condominiumId/package-descriptions") { setBody(body) }.body()
}

class ChatApiService(private val http: HttpClient) {
    suspend fun listThreads(condominiumId: String, updatedSince: String? = null): List<ChatThreadDto> =
        http.get("condominiums/$condominiumId/chat-threads") { updatedSince?.let { parameter("updatedSince", it) } }
            .body<PageDto<ChatThreadDto>>().items
    suspend fun createThread(condominiumId: String, body: CreateChatThreadRequestDto): ChatThreadDto =
        http.post("condominiums/$condominiumId/chat-threads") { setBody(body) }.body()
    suspend fun ensureCondoGroup(condominiumId: String): ChatThreadDto =
        http.get("condominiums/$condominiumId/chat-threads/group").body()
    suspend fun listMessages(threadId: String, since: String? = null): List<ChatMessageDto> =
        http.get("chat-threads/$threadId/messages") { since?.let { parameter("since", it) } }
            .body<PageDto<ChatMessageDto>>().items
    suspend fun sendMessage(threadId: String, body: SendMessageRequestDto): ChatMessageDto =
        http.post("chat-threads/$threadId/messages") { setBody(body) }.body()
}

class UploadsApiService(private val http: HttpClient) {
    suspend fun uploadImage(bytes: ByteArray, fileName: String = "image.jpg", mime: String = "image/jpeg"): UploadedImageRefDto =
        http.post("uploads/images") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, mime)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    }
                )
            )
        }.body()

    suspend fun uploadFile(bytes: ByteArray, fileName: String, mime: String = "application/pdf"): UploadedFileRefDto =
        http.post("uploads/files") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            "file",
                            bytes,
                            Headers.build {
                                append(HttpHeaders.ContentType, mime)
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                            },
                        )
                    }
                )
            )
        }.body()
}
