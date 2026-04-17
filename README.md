# Meu Condomínio

Aplicativo Kotlin Multiplatform (Android + iOS) para gestão de condomínios. Feito em
Compose Multiplatform, arquitetura Clean + MVVM, DI via Koin, Ktor client pronto para
APIs REST e SQLDelight pronto para cache offline. Esta versão roda com **repositórios
fake em memória** (fonte de verdade para o scaffold) para que o app funcione sem backend.

## Arquitetura

```
commonMain/kotlin/br/tec/wrcoder/meucondominio/
├─ core/                     utilitários (AppResult, AppClock, SecureStorage, Ids)
├─ di/                       módulos Koin (common + InitKoin)
├─ domain/
│  ├─ model/                 entidades (User, Notice, PackageItem, Reservation, …)
│  ├─ repository/            contratos
│  └─ usecase/               regras de negócio (cancelamento 7d, renovação, voto único)
├─ data/
│  ├─ remote/                HttpClientFactory (Ktor), CondoApiClient, DTOs
│  ├─ local/db/              DatabaseDriverFactory (SQLDelight expect/actual)
│  └─ repository/            implementações fake em memória + SampleData
└─ presentation/
   ├─ theme/                 Material3 theming
   ├─ navigation/            AppNavigator (state-based, sealed Route)
   ├─ common/                AppTopBar, EmptyState, etc.
   ├─ auth/                  Login, Cadastro de condomínio, Entrada via código
   ├─ main/                  HomeScreen (grid de features filtrado por perfil)
   └─ features/
      ├─ notices/            Avisos
      ├─ packages/           Encomendas
      ├─ spaces/             Espaços comuns + Reservas
      ├─ marketplace/        Anúncios (30 dias, renovação)
      ├─ moving/             Agendamento de mudanças
      ├─ files/              Arquivos (PDF)
      ├─ polls/              Enquetes (voto único + resultados)
      ├─ profile/            Perfil + gestão de membros da unidade
      └─ chat/               Chat interno (lista e conversa)
```

Cada feature: `FeatureViewModel` → expõe `StateFlow<UiState>` → tela Compose stateless
consome via `collectAsStateWithLifecycle()`. Permissões centralizadas em
`domain/model/Permission.kt` (`Permissions.canPerform(role, action)`).

## Perfis

- **Administrador**: gerencia avisos, espaços, arquivos, enquetes, decide mudanças, registra encomendas.
- **Supervisor**: mesma superfície do admin, exceto criação de espaços/arquivos/enquetes; cancela reservas fora do prazo; decide mudanças.
- **Morador**: reserva espaços, cria anúncios, solicita mudança, vota em enquetes, visualiza seus pacotes e arquivos, gerencia membros da sua unidade.

## Autenticação

1. Tela de login (e-mail + senha)
2. **Entrar com código do condomínio** (morador informa código único + identificador da unidade)
3. **Cadastrar novo condomínio** (gera código único, usuário criador vira administrador)

Sessão é persistida em armazenamento seguro (EncryptedSharedPreferences no Android,
NSUserDefaults no iOS — trocar por Keychain em produção).

### Dados de demonstração (seed)

- admin@demo.com / `123456` — Administrador
- supervisor@demo.com / `123456` — Supervisor
- morador@demo.com / `123456` — Morador (unidade 101)
- Código do condomínio: **DEMO1234**

## Infra já cabeada

- **Ktor Client** (`data/remote/HttpClientFactory.kt`) com ContentNegotiation,
  Auth (Bearer), Logging, Timeouts. Base URL em `ApiConfig.BASE_URL`.
- **SQLDelight** (`data/local/db/MeuCondominioDb.sq`) gera o schema comum e o driver
  é injetado por plataforma (Android/iOS).
- **Koin 4** para DI (common + Android/iOS modules).
- **kotlinx-datetime / kotlinx-coroutines / kotlinx-serialization**.

Para trocar os repositórios fake pela API real basta substituir os `Fake*Repository`
por implementações que usem o `CondoApiClient` (e/ou SQLDelight).

## Build

```bash
# Android debug
./gradlew :composeApp:assembleDebug

# iOS simulator compilation check
./gradlew :composeApp:compileKotlinIosSimulatorArm64
```

Abra `iosApp/` no Xcode para rodar no simulador.

## Próximos passos sugeridos

- Conectar `Fake*Repository` ao backend real via `CondoApiClient`.
- Persistir estado em SQLDelight (schema já pronto em `MeuCondominioDb.sq`).
- Push notifications: implementar `NotificationsRepository` em cada plataforma
  (FCM no Android, APNs + UNUserNotificationCenter no iOS).
- Upload de imagens/PDFs: pluginar `androidx.photopicker` / `UIDocumentPicker` atrás
  de um expect/actual e passar bytes para `FilesRepository.upload`.
- Substituir `AppNavigator` por `androidx.navigation.compose` se o app crescer.
- Adicionar Keychain real no iOS para `SecureStorage`.
