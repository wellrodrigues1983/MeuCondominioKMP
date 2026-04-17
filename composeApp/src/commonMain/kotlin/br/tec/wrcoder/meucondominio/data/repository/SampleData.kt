package br.tec.wrcoder.meucondominio.data.repository

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.newCondoCode
import br.tec.wrcoder.meucondominio.core.newId
import br.tec.wrcoder.meucondominio.core.toLocalDate
import br.tec.wrcoder.meucondominio.domain.model.CommonSpace
import br.tec.wrcoder.meucondominio.domain.model.Condominium
import br.tec.wrcoder.meucondominio.domain.model.CondoUnit
import br.tec.wrcoder.meucondominio.domain.model.Notice
import br.tec.wrcoder.meucondominio.domain.model.Poll
import br.tec.wrcoder.meucondominio.domain.model.PollOption
import br.tec.wrcoder.meucondominio.domain.model.PollStatus
import br.tec.wrcoder.meucondominio.domain.model.User
import br.tec.wrcoder.meucondominio.domain.model.UserRole
import kotlin.time.Duration.Companion.days

object SampleDataSeeder {
    fun seed(store: InMemoryStore, clock: AppClock) {
        if (store.condominiums.value.isNotEmpty()) return
        val now = clock.now()

        val condo = Condominium(
            id = newId(),
            name = "Residencial Demo",
            address = "Rua das Palmeiras, 123",
            code = "DEMO1234",
            createdAt = now,
        )
        val units = listOf("101", "102", "201", "202").map {
            CondoUnit(
                id = newId(),
                condominiumId = condo.id,
                identifier = it,
                block = "A",
            )
        }
        val admin = User(
            id = newId(),
            name = "Ana Admin",
            email = "admin@demo.com",
            phone = null,
            role = UserRole.ADMIN,
            condominiumId = condo.id,
            unitId = null,
            createdAt = now,
        )
        val supervisor = User(
            id = newId(),
            name = "Sérgio Supervisor",
            email = "supervisor@demo.com",
            phone = null,
            role = UserRole.SUPERVISOR,
            condominiumId = condo.id,
            unitId = null,
            createdAt = now,
        )
        val resident = User(
            id = newId(),
            name = "Rita Moradora",
            email = "morador@demo.com",
            phone = null,
            role = UserRole.RESIDENT,
            condominiumId = condo.id,
            unitId = units.first().id,
            createdAt = now,
        )

        store.condominiums.value = listOf(condo)
        store.units.value = units
        store.users.value = listOf(admin, supervisor, resident)
        store.passwords["admin@demo.com"] = "123456"
        store.passwords["supervisor@demo.com"] = "123456"
        store.passwords["morador@demo.com"] = "123456"

        store.notices.value = listOf(
            Notice(
                id = newId(),
                condominiumId = condo.id,
                title = "Bem-vindos ao Meu Condomínio",
                description = "Use o app para avisos, encomendas, reservas e muito mais.",
                authorId = admin.id,
                authorName = admin.name,
                createdAt = now,
            )
        )

        store.spaces.value = listOf(
            CommonSpace(
                id = newId(),
                condominiumId = condo.id,
                name = "Salão de Festas",
                description = "Capacidade para 40 pessoas, inclui cozinha e mobiliário.",
                price = 250.0,
                imageUrls = emptyList(),
            ),
            CommonSpace(
                id = newId(),
                condominiumId = condo.id,
                name = "Churrasqueira",
                description = "Área externa com churrasqueira e mesas.",
                price = 120.0,
                imageUrls = emptyList(),
            ),
        )

        store.polls.value = listOf(
            Poll(
                id = newId(),
                condominiumId = condo.id,
                question = "Devemos estender o horário da piscina aos sábados?",
                options = listOf(
                    PollOption(newId(), "Sim"),
                    PollOption(newId(), "Não"),
                    PollOption(newId(), "Tanto faz"),
                ),
                startsAt = now,
                endsAt = now + 7.days,
                status = PollStatus.OPEN,
                createdByUserId = admin.id,
                createdAt = now,
            )
        )

        // Suppress unused warning — toLocalDate is available for future sample data.
        now.toLocalDate(clock.timeZone())
        @Suppress("UNUSED_VARIABLE") val generatedCode = newCondoCode()
    }
}
