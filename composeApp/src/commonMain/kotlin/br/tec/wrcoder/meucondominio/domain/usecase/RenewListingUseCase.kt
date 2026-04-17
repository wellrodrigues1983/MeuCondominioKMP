package br.tec.wrcoder.meucondominio.domain.usecase

import br.tec.wrcoder.meucondominio.core.AppError
import br.tec.wrcoder.meucondominio.core.AppResult
import br.tec.wrcoder.meucondominio.core.asFailure
import br.tec.wrcoder.meucondominio.domain.model.Listing
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.repository.ListingRepository

class RenewListingUseCase(private val listings: ListingRepository) {
    suspend operator fun invoke(listing: Listing, actor: User): AppResult<Listing> {
        if (listing.authorUserId != actor.id) {
            return AppError.Forbidden("Somente o autor pode renovar o anúncio").asFailure()
        }
        return listings.renew(listing.id, actor.id)
    }
}
