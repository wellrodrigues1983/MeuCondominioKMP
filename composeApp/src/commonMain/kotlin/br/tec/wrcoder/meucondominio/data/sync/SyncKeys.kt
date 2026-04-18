package br.tec.wrcoder.meucondominio.data.sync

object Entities {
    const val NOTICE = "notice"
    const val SPACE = "space"
    const val RESERVATION = "reservation"
    const val LISTING = "listing"
    const val MOVING = "moving"
    const val FILE = "file"
    const val POLL = "poll"
    const val POLL_VOTE = "poll_vote"
    const val PACKAGE = "package"
    const val PACKAGE_DESCRIPTION = "package_description"
    const val CHAT_THREAD = "chat_thread"
    const val CHAT_MESSAGE = "chat_message"
    const val USER = "user"
    const val UNIT = "unit"
}

object Ops {
    const val CREATE = "create"
    const val UPDATE = "update"
    const val DELETE = "delete"
    const val CLOSE = "close"
    const val RENEW = "renew"
    const val APPROVE = "approve"
    const val REJECT = "reject"
    const val CANCEL_RESIDENT = "cancel_resident"
    const val CANCEL_STAFF = "cancel_staff"
    const val VOTE = "vote"
    const val PICKUP = "pickup"
    const val AVATAR = "avatar"
}

object SyncCursors {
    fun noticesOf(condoId: String) = "notices:$condoId"
    fun spacesOf(condoId: String) = "spaces:$condoId"
    fun listingsOf(condoId: String) = "listings:$condoId"
    fun movingsOf(condoId: String) = "movings:$condoId"
    fun movingsOfUnit(unitId: String) = "movings:unit:$unitId"
    fun filesOf(condoId: String) = "files:$condoId"
    fun pollsOf(condoId: String) = "polls:$condoId"
    fun packagesOf(condoId: String) = "packages:$condoId"
    fun packagesOfUnit(unitId: String) = "packages:unit:$unitId"
    fun chatThreadsOf(condoId: String) = "chatThreads:$condoId"
    fun chatMessagesOf(threadId: String) = "chatMessages:$threadId"
    fun reservationsOfSpace(spaceId: String) = "reservations:space:$spaceId"
    fun reservationsOfUnit(unitId: String) = "reservations:unit:$unitId"
    fun membersOfUnit(unitId: String) = "members:unit:$unitId"
}
