package br.tec.wrcoder.meucondominio.domain.model

/** Coarse-grained actions used throughout the app to authorize UI and use cases. */
enum class Action {
    NOTICE_MANAGE,
    PACKAGE_REGISTER,
    PACKAGE_VIEW_OWN,
    SPACE_MANAGE,
    RESERVATION_CREATE,
    RESERVATION_CANCEL_AFTER_DEADLINE,
    LISTING_CREATE,
    MOVING_REQUEST_CREATE,
    MOVING_REQUEST_DECIDE,
    FILE_MANAGE,
    POLL_MANAGE,
    POLL_VOTE,
    UNIT_MEMBER_MANAGE,
    CHAT_PARTICIPATE,
}

object Permissions {
    fun canPerform(role: UserRole, action: Action): Boolean = when (action) {
        Action.NOTICE_MANAGE -> role == UserRole.ADMIN || role == UserRole.SUPERVISOR
        Action.PACKAGE_REGISTER -> role == UserRole.ADMIN || role == UserRole.SUPERVISOR
        Action.PACKAGE_VIEW_OWN -> role == UserRole.RESIDENT
        Action.SPACE_MANAGE -> role == UserRole.ADMIN
        Action.RESERVATION_CREATE -> role == UserRole.RESIDENT
        Action.RESERVATION_CANCEL_AFTER_DEADLINE -> role == UserRole.ADMIN || role == UserRole.SUPERVISOR
        Action.LISTING_CREATE -> role == UserRole.RESIDENT
        Action.MOVING_REQUEST_CREATE -> role == UserRole.RESIDENT
        Action.MOVING_REQUEST_DECIDE -> role == UserRole.ADMIN || role == UserRole.SUPERVISOR
        Action.FILE_MANAGE -> role == UserRole.ADMIN
        Action.POLL_MANAGE -> role == UserRole.ADMIN
        Action.POLL_VOTE -> role == UserRole.RESIDENT
        Action.UNIT_MEMBER_MANAGE -> role == UserRole.RESIDENT
        Action.CHAT_PARTICIPATE -> true
    }
}
