package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.domain.model.ChatMessage
import br.tec.wrcoder.meucondominio.domain.model.ChatThread
import br.tec.wrcoder.meucondominio.domain.model.CommonSpace
import br.tec.wrcoder.meucondominio.domain.model.Condominium
import br.tec.wrcoder.meucondominio.domain.model.CondoUnit
import br.tec.wrcoder.meucondominio.domain.model.FileDoc
import br.tec.wrcoder.meucondominio.domain.model.Listing
import br.tec.wrcoder.meucondominio.domain.model.MovingRequest
import br.tec.wrcoder.meucondominio.domain.model.Notice
import br.tec.wrcoder.meucondominio.domain.model.PackageItem
import br.tec.wrcoder.meucondominio.domain.model.Poll
import br.tec.wrcoder.meucondominio.domain.model.PollVote
import br.tec.wrcoder.meucondominio.domain.model.Reservation
import br.tec.wrcoder.meucondominio.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow

/** Shared in-memory backing store used by the fake repositories. */
class InMemoryStore {
    val condominiums = MutableStateFlow<List<Condominium>>(emptyList())
    val units = MutableStateFlow<List<CondoUnit>>(emptyList())
    val users = MutableStateFlow<List<User>>(emptyList())
    val passwords = mutableMapOf<String, String>() // email -> password

    val notices = MutableStateFlow<List<Notice>>(emptyList())
    val packages = MutableStateFlow<List<PackageItem>>(emptyList())
    val spaces = MutableStateFlow<List<CommonSpace>>(emptyList())
    val reservations = MutableStateFlow<List<Reservation>>(emptyList())
    val listings = MutableStateFlow<List<Listing>>(emptyList())
    val movings = MutableStateFlow<List<MovingRequest>>(emptyList())
    val files = MutableStateFlow<List<FileDoc>>(emptyList())
    val polls = MutableStateFlow<List<Poll>>(emptyList())
    val pollVotes = MutableStateFlow<List<PollVote>>(emptyList())
    val chatThreads = MutableStateFlow<List<ChatThread>>(emptyList())
    val chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
}
