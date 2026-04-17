package br.tec.wrcoder.meucondominio.di

import br.tec.wrcoder.meucondominio.core.AppClock
import br.tec.wrcoder.meucondominio.core.BinaryStore
import br.tec.wrcoder.meucondominio.core.SystemAppClock
import br.tec.wrcoder.meucondominio.core.storage.SecureStorage
import br.tec.wrcoder.meucondominio.core.storage.createSecureStorage
import br.tec.wrcoder.meucondominio.data.remote.CondoApiClient
import br.tec.wrcoder.meucondominio.data.remote.createHttpClient
import br.tec.wrcoder.meucondominio.data.repository.FakeAuthRepository
import br.tec.wrcoder.meucondominio.data.repository.FakeChatRepository
import br.tec.wrcoder.meucondominio.data.repository.FakeCondominiumRepository
import br.tec.wrcoder.meucondominio.data.repository.FakeFilesRepository
import br.tec.wrcoder.meucondominio.data.repository.FakeListingRepository
import br.tec.wrcoder.meucondominio.data.repository.FakeMovingRepository
import br.tec.wrcoder.meucondominio.data.repository.FakeNoticeRepository
import br.tec.wrcoder.meucondominio.data.repository.FakePackageRepository
import br.tec.wrcoder.meucondominio.data.repository.FakePollsRepository
import br.tec.wrcoder.meucondominio.data.repository.FakeSpaceRepository
import br.tec.wrcoder.meucondominio.data.repository.InMemoryStore
import br.tec.wrcoder.meucondominio.data.repository.LoggingNotificationsRepository
import br.tec.wrcoder.meucondominio.data.repository.SampleDataSeeder
import br.tec.wrcoder.meucondominio.domain.repository.AuthRepository
import br.tec.wrcoder.meucondominio.domain.repository.ChatRepository
import br.tec.wrcoder.meucondominio.domain.repository.CondominiumRepository
import br.tec.wrcoder.meucondominio.domain.repository.FilesRepository
import br.tec.wrcoder.meucondominio.domain.repository.ListingRepository
import br.tec.wrcoder.meucondominio.domain.repository.MovingRepository
import br.tec.wrcoder.meucondominio.domain.repository.NoticeRepository
import br.tec.wrcoder.meucondominio.domain.repository.NotificationsRepository
import br.tec.wrcoder.meucondominio.domain.repository.PackageRepository
import br.tec.wrcoder.meucondominio.domain.repository.PollsRepository
import br.tec.wrcoder.meucondominio.domain.repository.SpaceRepository
import br.tec.wrcoder.meucondominio.domain.usecase.AuthorizeActionUseCase
import br.tec.wrcoder.meucondominio.domain.usecase.CancelReservationUseCase
import br.tec.wrcoder.meucondominio.domain.usecase.RenewListingUseCase
import br.tec.wrcoder.meucondominio.domain.usecase.VoteOnPollUseCase
import br.tec.wrcoder.meucondominio.presentation.auth.JoinCondominiumViewModel
import br.tec.wrcoder.meucondominio.presentation.auth.LoginViewModel
import br.tec.wrcoder.meucondominio.presentation.auth.RegisterCondominiumViewModel
import br.tec.wrcoder.meucondominio.presentation.features.chat.ChatThreadViewModel
import br.tec.wrcoder.meucondominio.presentation.features.chat.ChatThreadsViewModel
import br.tec.wrcoder.meucondominio.presentation.features.files.FilesViewModel
import br.tec.wrcoder.meucondominio.presentation.features.marketplace.MarketplaceViewModel
import br.tec.wrcoder.meucondominio.presentation.features.moving.MovingViewModel
import br.tec.wrcoder.meucondominio.presentation.features.notices.NoticesViewModel
import br.tec.wrcoder.meucondominio.presentation.features.packages.PackagesViewModel
import br.tec.wrcoder.meucondominio.presentation.features.polls.PollsViewModel
import br.tec.wrcoder.meucondominio.presentation.features.profile.ProfileViewModel
import br.tec.wrcoder.meucondominio.presentation.features.spaces.SpacesViewModel
import br.tec.wrcoder.meucondominio.presentation.main.HomeViewModel
import br.tec.wrcoder.meucondominio.presentation.navigation.AppNavigator
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

fun commonModule(): Module = module {
    single<AppClock> { SystemAppClock() }
    single<SecureStorage> { createSecureStorage() }
    single { BinaryStore() }
    single { InMemoryStore().also { SampleDataSeeder.seed(it, get()) } }

    single { createHttpClient(get()) }
    single { CondoApiClient(get()) }

    singleOf(::FakeAuthRepository) bind AuthRepository::class
    singleOf(::FakeCondominiumRepository) bind CondominiumRepository::class
    singleOf(::FakeNoticeRepository) bind NoticeRepository::class
    singleOf(::FakePackageRepository) bind PackageRepository::class
    singleOf(::FakeSpaceRepository) bind SpaceRepository::class
    singleOf(::FakeListingRepository) bind ListingRepository::class
    singleOf(::LoggingNotificationsRepository) bind NotificationsRepository::class
    singleOf(::FakeMovingRepository) bind MovingRepository::class
    singleOf(::FakeFilesRepository) bind FilesRepository::class
    singleOf(::FakePollsRepository) bind PollsRepository::class
    singleOf(::FakeChatRepository) bind ChatRepository::class

    factoryOf(::CancelReservationUseCase)
    factoryOf(::RenewListingUseCase)
    factoryOf(::VoteOnPollUseCase)
    factoryOf(::AuthorizeActionUseCase)

    single { AppNavigator() }

    factoryOf(::LoginViewModel)
    factoryOf(::RegisterCondominiumViewModel)
    factoryOf(::JoinCondominiumViewModel)
    factoryOf(::HomeViewModel)
    factoryOf(::NoticesViewModel)
    factoryOf(::PackagesViewModel)
    factoryOf(::SpacesViewModel)
    factoryOf(::MarketplaceViewModel)
    factoryOf(::MovingViewModel)
    factoryOf(::FilesViewModel)
    factoryOf(::PollsViewModel)
    factoryOf(::ProfileViewModel)
    factoryOf(::ChatThreadsViewModel)
    factoryOf(::ChatThreadViewModel)
}
