package br.tec.wrcoder.meucondominio.domain.usecase

import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.core.asSuccess
import br.tec.wrcoder.meucondominio.domain.model.Action
import br.tec.wrcoder.meucondominio.domain.model.Permissions
import br.tec.wrcoder.meucondominio.domain.model.UserRole

class AuthorizeActionUseCase {
    operator fun invoke(role: UserRole, action: Action): AppResult<Unit> =
        if (Permissions.canPerform(role, action)) Unit.asSuccess()
        else AppError.Forbidden().asFailure()
}
