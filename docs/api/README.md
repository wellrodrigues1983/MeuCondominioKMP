# API Meu Condomínio — Guia de Implementação (Backend)

Este documento guia a construção da API REST do app **Meu Condomínio** (cliente
Kotlin Multiplatform + Compose). A fonte de verdade dos contratos é o arquivo
[`openapi.yaml`](./openapi.yaml) (OpenAPI 3.1) — este README explica os porquês,
padrões transversais e fluxos que o YAML sozinho não comunica.

---

## 1. Visão geral da arquitetura

- **Estilo:** REST sobre HTTP/1.1 (HTTP/2 em produção via gateway). JSON em UTF-8.
- **Prefixo global:** todos os endpoints vivem sob `/v1`. Evoluções incompatíveis
  sobem para `/v2`. Evoluções compatíveis (campos opcionais novos) ficam em `/v1`.
- **Autenticação:** JWT Bearer com par `accessToken` (curto) + `refreshToken`
  (longo), com rotação.
- **Paginação:** cursor-based (`cursor`, `limit`, `nextCursor`). Sem offsets.
- **Sincronização:** incremental via `updatedSince` + tombstones (`deleted: true`).
- **Conflitos:** resolução do servidor é **last-write-wins** baseado em
  `updatedAt` do servidor.
- **Upload de mídia:** multipart servido pelo backend (sem URLs presigned S3).
- **WebSocket (futuro):** ver §10.
- **Identificadores:** UUID v4 em todos os recursos.
- **Datas:**
  - Timestamps (`createdAt`, `updatedAt`, `sentAt`, ...) — ISO-8601 com offset,
    sempre UTC (`2026-04-17T12:34:56Z`).
  - Datas puras (reserva de espaço, `LocalDate`) — `YYYY-MM-DD`.
  - Data+hora **sem** timezone (agendamento de mudança, `LocalDateTime`) —
    `YYYY-MM-DDTHH:MM:SS`.
- **Versionamento de entidades:** todo recurso mutável tem `version: int`,
  incrementado pelo servidor a cada mutação; `updatedAt` também é recalculado.

---

## 2. Autenticação

### 2.1 Tokens

| Token          | TTL sugerido | Conteúdo                                           |
|----------------|--------------|----------------------------------------------------|
| `accessToken`  | 15 min       | `userId`, `role`, `condominiumId`, `unitId`, `iat`, `exp` |
| `refreshToken` | 30 dias      | `jti`, `userId`, `exp`; armazenado hash+rotável em `auth_refresh_tokens` |

- `Authorization: Bearer <accessToken>` em todas as chamadas autenticadas.
- O **refresh token é rotativo**: ao chamar `/auth/refresh`, o backend invalida
  o antigo e emite novo par. Se receber um refresh já invalidado, resposta
  `401 REFRESH_REVOKED` — forçar logout no cliente.
- `POST /auth/logout` revoga o refresh atual.
- **Toda resposta `401` autenticada deve incluir o header**
  `WWW-Authenticate: Bearer realm="api"` (e opcionalmente `error="invalid_token"`
  quando o JWT está expirado). Sem esse header, clientes que usam o plugin
  `Auth` do Ktor (e outras libs HTTP padrão) não disparam o refresh
  automático — o cliente do app tem uma defesa manual, mas outros consumidores
  da API dependem do header.

### 2.2 Fluxo de login

```
Cliente                                 Backend
   | --- POST /auth/login {email,pwd} ---> |
   | <------ 200 {access, refresh, user} - |
   | ... uso normal, cada request manda Authorization ...
   | --- GET /v1/xxx (access expirado) --->|
   | <------------- 401 TOKEN_EXPIRED ---- |
   | --- POST /auth/refresh {refresh} ---->|
   | <------ 200 {access, refresh} ------- |
   | --- GET /v1/xxx (retry) ------------->|
   | <------------- 200 OK --------------- |
```

### 2.3 Registro de condomínio vs. ingresso

- `POST /auth/register-condominium` — cria `Condominium` + `User (ADMIN)` na
  mesma transação. Gera `condominiums.code` único (ex.: `base32` 8 chars).
- `POST /auth/join-condominium` — valida `condoCode` e `unitIdentifier`, cria
  `User (RESIDENT)` vinculado à unidade. Se `unitIdentifier` não existir,
  `404 UNIT_NOT_FOUND`.

---

## 3. Protocolo offline-first do app

O cliente é **offline-first**: toda operação é persistida localmente primeiro e
só depois sincronizada. O backend não precisa conhecer detalhes do cliente,
mas precisa **respeitar os contratos abaixo** para que a sync funcione.

### 3.1 Outbox → retry

Cada mutação (POST/PATCH/DELETE) é enfileirada no cliente. Ao ficar online:

1. Cliente drena a fila em ordem FIFO por feature.
2. Cada request carrega header opcional `Idempotency-Key: <uuid-do-outbox-item>`.
3. O backend **deve** persistir o par `(Idempotency-Key, hash-do-body, resposta)`
   por pelo menos 24h. Requisição repetida com a mesma chave retorna a resposta
   anterior (mesmo status, mesmo body) sem reprocessar.
4. Respostas `5xx` ou timeout fazem o cliente tentar novamente com backoff
   exponencial.

### 3.2 Pull incremental (`updatedSince`)

Para cada lista observável (notices, packages, listings, movings, ...):

1. Cliente guarda o maior `updatedAt` já recebido: `lastSyncAt`.
2. Cliente chama `GET /.../xxx?updatedSince=<lastSyncAt>&limit=200`.
3. Backend retorna:
   - Registros com `updatedAt >= updatedSince`, incluindo soft-deletes como
     tombstone `{ id, deleted: true, updatedAt, version }`.
   - Ordenação **obrigatória**: `updatedAt ASC, id ASC` (desempate estável).
4. Cliente aplica os diffs no banco local, remove tombstones e avança
   `lastSyncAt` para o maior `updatedAt` recebido.

### 3.3 Regra de conflito (last-write-wins)

Quando um PATCH chega ao servidor:

- Servidor lê a versão atual, aplica o merge shallow, **incrementa `version`**,
  atualiza `updatedAt = now()` e devolve o objeto canônico.
- Cliente **sempre** substitui o estado local pelo objeto retornado.
- Se o cliente quiser detectar conflitos, pode mandar `If-Match: <version>` no
  header; backend retorna `409 VERSION_MISMATCH` se `version` atual for maior.
  No MVP o cliente ignora (last-write-wins puro); o header é aceito e usado só
  onde o produto exigir (ex.: edição concorrente de avisos).

---

## 4. Paginação

Query params:

| Nome   | Tipo    | Default | Máx | Observações                       |
|--------|---------|---------|-----|-----------------------------------|
| cursor | string  | —       | —   | Opaco. Cliente nunca interpreta.  |
| limit  | integer | 50      | 200 | Respeite o cap no servidor.       |

Resposta:

```json
{
  "items": [ /* ... */ ],
  "nextCursor": "eyJpZCI6Ii4uLiIsInQiOiIuLi4ifQ"
}
```

- `nextCursor == null` → fim da coleção.
- Cursor recomendado: `base64(json({lastId, lastUpdatedAt}))`. Deve ser estável
  sob a ordenação `updatedAt ASC, id ASC`.
- `updatedSince` e `cursor` podem ser combinados.

---

## 5. Upload de mídia

| Endpoint            | Campo | Tipos aceitos                      | Tamanho máx |
|---------------------|-------|------------------------------------|-------------|
| `POST /uploads/images` | `file` | `image/jpeg`, `image/png`, `image/webp` | 5 MB    |
| `POST /uploads/files`  | `file` | `application/pdf`                       | 20 MB   |
| `PATCH /me/avatar`     | `image` | `image/jpeg`, `image/png`, `image/webp` | 5 MB |

- Content-Type da requisição: `multipart/form-data`.
- Retorno sempre inclui `id` e `url` absoluta servida pelo backend
  (proxy/CDN). **Não** use presigned S3 — o app depende de Bearer para
  privacidade/controle de acesso.
- Rejeitar MIME fora da lista com `422 UNSUPPORTED_MEDIA` e ultrapassar tamanho
  com `413 FILE_TOO_LARGE`.
- Recomendado gerar thumbnails assíncronos para imagens; não bloquear a resposta.

### 5.1 Upload em feature específica

Alguns endpoints aceitam tanto `application/json` (com `imageUrls` já subidas
via `/uploads/images`) quanto `multipart/form-data` direto (arquivos + campos):

- `POST /condominiums/{condoId}/spaces` — campo `images[]`
- `POST /condominiums/{condoId}/listings` — campo `images[]`
- `POST /condominiums/{condoId}/files` — campo `file` (obrigatório)

O cliente escolhe o fluxo mais conveniente; o backend deve suportar ambos.

---

## 6. Autorização — matriz de permissões

Papéis: `ADMIN > SUPERVISOR > RESIDENT` (herança não é automática — cada ação
tem regra explícita).

| Ação                                                   | ADMIN | SUPERVISOR | RESIDENT |
|--------------------------------------------------------|:-----:|:----------:|:--------:|
| Gerenciar avisos (`NOTICE_MANAGE`)                     |  Sim  |    Sim     |   Não    |
| Registrar encomendas (`PACKAGE_REGISTER`)              |  Sim  |    Sim     |   Não    |
| Ver encomendas da própria unidade (`PACKAGE_VIEW_OWN`) |  Sim  |    Sim     |   Sim    |
| Gerenciar espaços (`SPACE_MANAGE`)                     |  Sim  |    Não     |   Não    |
| Criar reserva (`RESERVATION_CREATE`)                   |  Não  |    Não     |   Sim    |
| Cancelar reserva após prazo (`RESERVATION_CANCEL_AFTER_DEADLINE`) | Sim | Sim |   Não    |
| Criar anúncio (`LISTING_CREATE`)                       |  Não  |    Não     |   Sim    |
| Solicitar mudança (`MOVING_REQUEST_CREATE`)            |  Não  |    Não     |   Sim    |
| Decidir mudança (`MOVING_REQUEST_DECIDE`)              |  Sim  |    Sim     |   Não    |
| Gerenciar arquivos (`FILE_MANAGE`)                     |  Sim  |    Não     |   Não    |
| Gerenciar enquetes (`POLL_MANAGE`)                     |  Sim  |    Não     |   Não    |
| Votar em enquete (`POLL_VOTE`)                         |  Não  |    Não     |   Sim    |
| Gerenciar membros da unidade (`UNIT_MEMBER_MANAGE`)    |  Sim* |    Sim*    |   Sim    |
| Participar do chat (`CHAT_PARTICIPATE`)                |  Sim  |    Sim     |   Sim    |

\* ADMIN/SUPERVISOR só podem gerenciar membros dentro do próprio condomínio.

Regras adicionais:

- Toda request é sempre escopada por `condominiumId` do token — o servidor
  **deve** rejeitar acesso a recursos de outro condomínio com `403 CROSS_TENANT`.
- Recursos "do próprio usuário" (ex.: close/renew listing, cancel-by-resident)
  validam `authorUserId == session.userId`.

---

## 7. Envelope de erro e códigos HTTP

Formato único:

```json
{
  "error": {
    "code": "STRING_CODE",
    "message": "Mensagem pt-BR segura para exibir ao usuário",
    "details": { "fields": { "email": "formato inválido" } }
  }
}
```

### 7.1 Mapeamento AppError (cliente) ↔ HTTP

| AppError (cliente) | HTTP            | `error.code` exemplo                   |
|--------------------|-----------------|----------------------------------------|
| `Network`          | 5xx, timeout    | `INTERNAL_ERROR`, `SERVICE_UNAVAILABLE` |
| `Unauthorized`     | 401             | `TOKEN_EXPIRED`, `INVALID_CREDENTIALS`, `REFRESH_REVOKED` |
| `Forbidden`        | 403             | `FORBIDDEN`, `CROSS_TENANT`, `DEADLINE_PASSED`, `RESULTS_NOT_PUBLIC_YET` |
| `NotFound`         | 404             | `NOT_FOUND`, `UNIT_NOT_FOUND`          |
| `Validation`       | 400 / 409 / 413 / 415 / 422 | `VALIDATION_FAILED`, `ALREADY_VOTED`, `VERSION_MISMATCH`, `FILE_TOO_LARGE`, `UNSUPPORTED_MEDIA` |
| `Unknown`          | qualquer outro  | —                                      |

### 7.2 Códigos canônicos mínimos

| Code                     | HTTP | Uso                                                   |
|--------------------------|------|-------------------------------------------------------|
| `TOKEN_EXPIRED`          | 401  | Access token expirado — cliente chama `/auth/refresh` |
| `INVALID_CREDENTIALS`    | 401  | Email/senha inválidos                                 |
| `REFRESH_REVOKED`        | 401  | Refresh inválido ou já rotacionado                    |
| `FORBIDDEN`              | 403  | Permissão insuficiente                                |
| `CROSS_TENANT`           | 403  | Acesso a outro condomínio                             |
| `DEADLINE_PASSED`        | 403  | Prazo de cancelamento por morador expirado            |
| `RESULTS_NOT_PUBLIC_YET` | 403  | Resultado de enquete ainda não liberado               |
| `NOT_FOUND`              | 404  | Recurso inexistente                                   |
| `UNIT_NOT_FOUND`         | 404  | `unitIdentifier` não encontrado no ingresso           |
| `VALIDATION_FAILED`      | 422  | Dados inválidos — detalhe em `details.fields`         |
| `FILE_TOO_LARGE`         | 413  | Upload acima do limite                                |
| `UNSUPPORTED_MEDIA`      | 415  | MIME não permitido                                    |
| `ALREADY_VOTED`          | 409  | Usuário já votou na enquete                           |
| `VERSION_MISMATCH`       | 409  | `If-Match` não bate com a versão atual                |
| `INTERNAL_ERROR`         | 500  | Erro inesperado                                       |
| `SERVICE_UNAVAILABLE`    | 503  | Dependência fora do ar                                |

---

## 8. Exemplos curl

Em todos os exemplos, `$TOKEN` é um `accessToken` válido.

### 8.1 Login

```bash
curl -X POST https://api.meucondominio.app/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@exemplo.com","password":"supersenha"}'
```

### 8.2 Refresh

```bash
curl -X POST https://api.meucondominio.app/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"eyJhbGciOi..."}'
```

### 8.3 Ingresso em condomínio

```bash
curl -X POST https://api.meucondominio.app/v1/auth/join-condominium \
  -H 'Content-Type: application/json' \
  -d '{
    "condoCode": "ABCD1234",
    "unitIdentifier": "101-A",
    "name": "Maria Silva",
    "email": "maria@exemplo.com",
    "password": "supersenha",
    "phone": "+5511999998888"
  }'
```

### 8.4 Pull incremental de avisos

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://api.meucondominio.app/v1/condominiums/$CID/notices?updatedSince=2026-04-15T00:00:00Z&limit=200"
```

### 8.5 Criar aviso

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"title":"Manutenção no elevador","description":"Sábado 08h–12h"}' \
  "https://api.meucondominio.app/v1/condominiums/$CID/notices"
```

### 8.6 Registrar encomenda

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"unitId":"'$UID'","description":"Caixa Amazon","carrier":"Correios"}' \
  "https://api.meucondominio.app/v1/condominiums/$CID/packages"
```

### 8.7 Reservar espaço

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"unitId":"'$UID'","date":"2026-05-18"}' \
  "https://api.meucondominio.app/v1/spaces/$SID/reservations"
```

### 8.8 Criar anúncio com imagens (multipart)

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -F 'title=Sofá 3 lugares' \
  -F 'description=Semi-novo' \
  -F 'price=450.00' \
  -F 'images=@/tmp/sofa1.jpg' \
  -F 'images=@/tmp/sofa2.jpg' \
  "https://api.meucondominio.app/v1/condominiums/$CID/listings"
```

### 8.9 Solicitar mudança

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"unitId":"'$UID'","scheduledFor":"2026-05-02T14:00:00"}' \
  "https://api.meucondominio.app/v1/condominiums/$CID/movings"
```

### 8.10 Votar em enquete

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"optionId":"'$OPT'"}' \
  "https://api.meucondominio.app/v1/polls/$PID/vote"
```

### 8.10.1 Listar membros do condomínio (para iniciar chat)

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://api.meucondominio.app/v1/condominiums/$CID/members?role=SUPERVISOR"
```

Resposta: envelope padrão de paginação com `items: [User]`. O filtro `role`
é opcional (`ADMIN`, `SUPERVISOR`, `RESIDENT`). Usado pelo app para popular a
lista de contatos disponíveis para abrir uma conversa. Contrato completo,
consulta SQL de referência e check-list de implementação:
[`CONDOMINIUM_MEMBERS.md`](./CONDOMINIUM_MEMBERS.md).

### 8.11 Enviar mensagem de chat

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"text":"Alguém viu a chave na garagem?"}' \
  "https://api.meucondominio.app/v1/chat-threads/$TID/messages"
```

### 8.11.1 Obter a thread do grupo do condomínio

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://api.meucondominio.app/v1/condominiums/$CID/members"
```

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://api.meucondominio.app/v1/condominiums/$CID/chat-threads/group"
```

Idempotente. Retorna a thread singleton `kind = "CONDO_GROUP"` (criando na
primeira chamada). O servidor mantém `participantUserIds` em sincronia com
todos os moradores ativos do condomínio — o cliente **não** gerencia essa
membership. Contrato completo: [`CONDO_GROUP_CHAT.md`](./CONDO_GROUP_CHAT.md).

### 8.12 Upload de PDF

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -F 'file=@/tmp/convencao.pdf' \
  -F 'title=Convenção do condomínio' \
  -F 'description=Versão 2026' \
  "https://api.meucondominio.app/v1/condominiums/$CID/files"
```

---

## 9. Ambientes e configuração

### 9.1 Ambientes

| Ambiente   | Base URL                                     | Observações                     |
|------------|----------------------------------------------|---------------------------------|
| dev        | `http://localhost:8080/v1`                   | Banco efêmero, logs verbose     |
| staging    | `https://staging.api.meucondominio.app/v1`   | Deploy automático de `main`     |
| produção   | `https://api.meucondominio.app/v1`           | Release por tag                 |

### 9.2 Variáveis de ambiente sugeridas

| Variável                 | Exemplo                              | Descrição                             |
|--------------------------|--------------------------------------|---------------------------------------|
| `DATABASE_URL`           | `postgresql://user:pwd@host/db`      | Postgres principal                    |
| `REDIS_URL`              | `redis://host:6379/0`                | Cache + tokens revogados              |
| `JWT_ISSUER`             | `meucondominio`                      | `iss` claim                           |
| `JWT_AUDIENCE`           | `meucondominio-app`                  | `aud` claim                           |
| `JWT_ACCESS_SECRET`      | `<32+ bytes>`                        | HS256 ou chave pública RS256          |
| `JWT_REFRESH_SECRET`     | `<32+ bytes>`                        | Separado do access                    |
| `ACCESS_TOKEN_TTL_SEC`   | `900`                                | 15 min                                |
| `REFRESH_TOKEN_TTL_SEC`  | `2592000`                            | 30 dias                               |
| `STORAGE_BUCKET`         | `mc-media-prod`                      | S3-compatível                         |
| `STORAGE_CDN_BASE_URL`   | `https://cdn.meucondominio.app`      | Prefixo das `url` retornadas          |
| `MAX_UPLOAD_IMAGE_MB`    | `5`                                  | Cap de imagem                         |
| `MAX_UPLOAD_FILE_MB`     | `20`                                 | Cap de PDF                            |
| `IDEMPOTENCY_TTL_HOURS`  | `24`                                 | Janela de deduplicação                |
| `LOG_LEVEL`              | `INFO`                               | `DEBUG` em dev                        |
| `CORS_ALLOWED_ORIGINS`   | `https://app.meucondominio.app`      | CSV de origens                        |

---

## 10. Chat em tempo real (WebSocket)

O cliente usa REST para histórico/envio e WebSocket para **push** de novas
mensagens e atualizações de thread. A especificação completa do canal está em
[`CHAT_WEBSOCKET.md`](./CHAT_WEBSOCKET.md). Resumo:

- Endpoint: `wss://<host>/v1/ws/chat?token=<accessToken>` (dev: `ws://localhost:8080/v1/ws/chat`).
- Handshake autenticado via `token` no query string (JWT de acesso). Depois do
  upgrade o gateway deve derrubar a conexão quando `exp` expirar.
- O servidor inscreve automaticamente o usuário nas threads em que participa
  (filtradas por `condominiumId` do token). `subscribe` é opcional para
  refinar o filtro.
- Eventos server→client: `message.new`, `thread.update` (payloads idênticos aos
  DTOs REST — ver `openapi.yaml`).
- Eventos client→server: `ping` (heartbeat 30 s, resposta `pong`) e
  `subscribe` opcional.
- REST permanece a **fonte de verdade**: o cliente, ao (re)conectar, faz pull
  incremental via `updatedSince` antes de consumir eventos.

---

## 11. Check-list para o backend

- [ ] Migrations com tabelas de sync (`version int`, `updated_at timestamptz`,
      `deleted_at timestamptz` — queries usam `COALESCE(deleted_at, updated_at)`).
- [ ] Índices: `(condominium_id, updated_at, id)` em todas as coleções.
- [ ] Row-level tenancy: middleware rejeita acesso fora de `session.condominiumId`.
- [ ] Mecanismo central de idempotência (`Idempotency-Key` → cache Redis 24h).
- [ ] Job que marca `listings` como `EXPIRED` quando `expiresAt < now()`.
- [ ] Job que abre/fecha `polls` em `startsAt`/`endsAt`.
- [ ] Rate limit por IP e por usuário (sugestão: 60 req/min autenticado).
- [ ] Auditoria: logar `userId`, `role`, `path`, `statusCode`, `requestId`.
- [ ] Métricas Prometheus: latência p95 por rota, erros 5xx, tamanho de outbox.
- [ ] OpenAPI publicada em `/v1/openapi.yaml` e Swagger UI em `/v1/docs` (dev/staging apenas).
