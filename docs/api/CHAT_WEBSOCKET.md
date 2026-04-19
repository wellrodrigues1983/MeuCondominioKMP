# Chat em tempo real — WebSocket

Este documento é o contrato completo do canal WebSocket usado pelo app
**Meu Condomínio** para receber mensagens de chat em tempo real. É complemento
do [`README.md`](./README.md) (§10) e do `openapi.yaml` (endpoints REST do chat).

---

## 0. TL;DR — mínimo que o backend precisa entregar

Para o cliente atual funcionar com delivery instantâneo, basta isto:

1. **Endpoint:** `GET /v1/ws/chat?token=<accessToken>` aceita upgrade WebSocket.
2. **Autenticação:** valida o JWT do query string no handshake. Se inválido,
   fecha com close code `4001`. Extrai `userId` e `condominiumId` do claim.
3. **Subscrição automática:** após o upgrade, associa a conexão a todas as
   threads do condomínio em que o usuário é participante (inclui
   `CONDO_GROUP`). O cliente **não** envia `subscribe`.
4. **Emitir `message.new`** para cada participante de uma thread toda vez que
   uma nova mensagem é persistida (inclusive o autor, para reconciliação):
   ```json
   { "type": "message.new", "message": <ChatMessageDto idêntico ao REST> }
   ```
   Emitir **depois** que `updatedAt` já está gravado no banco.
5. **Emitir `thread.update`** quando uma thread é criada, tem participantes
   alterados, último preview mudado, ou deletada:
   ```json
   { "type": "thread.update", "thread": <ChatThreadDto idêntico ao REST> }
   ```
6. **Heartbeat:** o cliente envia `{"type":"ping"}` a cada 30s. O servidor
   pode ignorar, responder `{"type":"pong"}`, ou usar isso para detectar idle
   (se preferir, fecha com `4004` após 60s sem ping).
7. **Somente JSON UTF-8.** Nada de frames binários. Qualquer campo
   desconhecido no JSON deve ser ignorado pelo cliente — o backend pode
   adicionar campos futuros livremente.
8. **Tenancy:** nunca emita evento de thread cujo `condominiumId` seja
   diferente do token. Nunca emita evento de mensagem cujo thread o usuário
   não participa.

**Payloads DTO** (exatamente os mesmos do REST):

```jsonc
// ChatMessageDto
{
  "id": "uuid",
  "threadId": "uuid",
  "senderUserId": "uuid",
  "senderName": "string",
  "text": "string",
  "sentAt": "2026-04-18T10:12:34Z",    // ISO-8601 UTC
  "updatedAt": "2026-04-18T10:12:34Z", // ISO-8601 UTC, ≥ sentAt
  "version": 0,
  "deleted": false
}

// ChatThreadDto
{
  "id": "uuid",
  "condominiumId": "uuid",
  "title": "string",
  "participantUserIds": ["uuid", "..."],
  "lastMessagePreview": "string | null",
  "lastMessageAt": "ISO-8601 | null",
  "updatedAt": "ISO-8601",
  "version": 0,
  "deleted": false,
  "kind": "DIRECT" | "CONDO_GROUP"
}
```

**Fluxo de envio:**
```
A: POST /v1/chat-threads/{id}/messages  (body: { text, clientRefId })
Backend:
  1. Se existir msg com id == clientRefId → retorna ela (idempotência).
  2. Senão, persiste usando clientRefId como id.
  3. Emite "message.new" a todos os participantes da thread (inclui A).
  4. Responde 200/201 com o ChatMessageDto canônico.
```

O cliente já cobre os fallbacks: pull REST incremental a cada 1,5s no chat
aberto + pull imediato ao reconectar. O WS só entra para ficar *instantâneo*.
As seções abaixo detalham tudo; o que está acima é o suficiente para a
primeira implementação.

---

## 1. Princípios

- **REST é a fonte de verdade.** O WebSocket entrega apenas *push* de novas
  mensagens e atualizações de thread. Qualquer reconexão dispara um pull
  incremental (`updatedSince`) antes de confiar nos eventos recebidos.
- **Payloads idênticos aos DTOs REST.** `message.new` carrega exatamente o
  mesmo JSON de `ChatMessageDto`; `thread.update` carrega o mesmo JSON de
  `ChatThreadDto`. Versão e `updatedAt` são gerados pelo servidor.
- **Tenancy rigorosa.** O gateway **nunca** entrega eventos cujo
  `condominiumId` seja diferente do `condominiumId` do token.
- **Apenas JSON texto (UTF-8).** Não use frames binários; eles devem ser
  descartados pelo servidor.

---

## 2. Endpoint e handshake

| Ambiente   | URL                                              |
|------------|--------------------------------------------------|
| dev        | `ws://localhost:8080/v1/ws/chat?token=<access>`  |
| staging    | `wss://staging.api.meucondominio.app/v1/ws/chat?token=<access>` |
| produção   | `wss://api.meucondominio.app/v1/ws/chat?token=<access>` |

- Protocolo: WebSocket (RFC 6455) sobre TLS em staging/produção.
- Subprotocol: *nenhum* (`Sec-WebSocket-Protocol` vazio).
- Limite de tamanho do frame de texto: 64 KiB. Frames maiores devem ser
  rejeitados com close code `1009` (`MESSAGE_TOO_BIG`).
- Limite de conexões simultâneas por usuário: sugerido 5. Conexão excedente
  recebe close code `4003` (ver §6).

### 2.1 Autenticação

- O `accessToken` JWT é enviado **somente no query string** (`?token=...`).
  Isso é compatível com navegadores e com a ausência de headers customizados
  em várias APIs de WS.
- O servidor **deve** validar o JWT **no handshake**. Se falhar, responder ao
  upgrade com `401` (ou aceitar upgrade e fechar imediatamente com `4001`).
- Após o upgrade, o servidor **deve** armazenar `userId`, `role`,
  `condominiumId` derivados do token e usar esses valores para filtrar
  eventos.
- Quando o token expirar durante uma sessão ativa (verificação periódica), o
  servidor fecha com `4002 TOKEN_EXPIRED` — o cliente então refaz o fluxo de
  refresh REST e reconecta.

### 2.2 Subscrição automática

No upgrade bem-sucedido, o servidor **já subscreve** o usuário em todas as
threads em que ele participa (consulta equivalente a
`GET /condominiums/{condoId}/chat-threads` filtrada por participação). Isso
evita uma rodada extra de mensagens `subscribe` no caso comum.

---

## 3. Eventos server → client

Todos os payloads são objetos JSON com campo `type`. Campos adicionais no
futuro **devem** ser ignorados pelo cliente.

### 3.1 `message.new`

Emitido quando uma nova mensagem é persistida em uma thread que o usuário
participa (inclusive a própria, para confirmação e reconciliação).

```json
{
  "type": "message.new",
  "message": {
    "id": "8bdb3a66-06b0-4f3a-b86a-9e0c3b9b8f33",
    "threadId": "3c2f9f12-39d0-45cd-b0aa-2e8c73b1b16c",
    "senderUserId": "d64a8e72-4f3f-4e3f-b1b1-1e3e1b3e3b3b",
    "senderName": "Maria Silva",
    "text": "Alguém viu a chave na garagem?",
    "sentAt": "2026-04-18T10:12:34Z",
    "updatedAt": "2026-04-18T10:12:34Z",
    "version": 0,
    "deleted": false
  }
}
```

Notas:

- O servidor **deve** emitir este evento **depois** de `updatedAt` estar
  persistido no banco — o cliente usa esse campo para conciliar com o próprio
  cursor de sync.
- Mensagens deletadas também chegam aqui com `deleted: true` e `updatedAt`
  novo (tombstone). O cliente marca a linha local como excluída.

### 3.2 `thread.update`

Emitido quando uma thread é criada, tem metadados alterados (título,
participantes, último preview/`lastMessageAt`) ou é deletada.

```json
{
  "type": "thread.update",
  "thread": {
    "id": "3c2f9f12-39d0-45cd-b0aa-2e8c73b1b16c",
    "condominiumId": "7b1d57e0-12b4-4f44-b34a-1a6a4d2e5e11",
    "title": "Maria Silva · Carlos (Supervisor)",
    "participantUserIds": [
      "d64a8e72-4f3f-4e3f-b1b1-1e3e1b3e3b3b",
      "91f8e3a2-9a84-47ad-af5d-d3a54f83b3b1"
    ],
    "lastMessagePreview": "Alguém viu a chave na garagem?",
    "lastMessageAt": "2026-04-18T10:12:34Z",
    "updatedAt": "2026-04-18T10:12:34Z",
    "version": 3,
    "deleted": false
  }
}
```

### 3.3 `pong`

Resposta ao `ping` do cliente (§4.1). Payload mínimo:

```json
{ "type": "pong" }
```

### 3.4 `error`

Usado para erros que **não** encerram a conexão (ex.: payload inválido em um
`subscribe`). Formato alinhado ao envelope REST (§7 do README):

```json
{
  "type": "error",
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "threadIds must be a non-empty list of uuid",
    "details": { "field": "threadIds" }
  }
}
```

Para erros fatais, fechar a conexão com o close code apropriado (§6) em vez
de emitir `error`.

---

## 4. Eventos client → server

### 4.1 `ping`

Heartbeat obrigatório a cada **30 s**. O cliente envia:

```json
{ "type": "ping" }
```

O servidor responde com `pong` (§3.3). Se não houver `ping` por **60 s**, o
servidor **deve** fechar com `4004 IDLE_TIMEOUT`.

> Observação: o cliente Ktor também envia Frame-level pings (`pingIntervalMillis`).
> O backend pode responder aos dois; o heartbeat JSON é o oficial para
> compatibilidade com gateways que não propagam frames de controle.

### 4.2 `subscribe` (opcional)

Reduz o conjunto de threads pelas quais o cliente quer receber eventos. Útil
para apps que abrem uma thread específica e querem ignorar ruído de outras.

```json
{
  "type": "subscribe",
  "threadIds": [
    "3c2f9f12-39d0-45cd-b0aa-2e8c73b1b16c"
  ]
}
```

Regras:

- `threadIds` é uma lista de UUIDs. Lista vazia **reverte** para o default
  (subscrição em todas as threads que o usuário participa).
- O servidor **deve** validar que o usuário participa de cada thread
  requisitada; em caso de falha, emitir `error` com código `FORBIDDEN` e
  ignorar a requisição.
- A última `subscribe` vigente substitui as anteriores.

### 4.3 `unsubscribe` (opcional)

```json
{ "type": "unsubscribe", "threadIds": ["..."] }
```

Remove IDs do filtro ativo. Se resultar em lista vazia, o servidor volta ao
default (tudo).

---

## 5. Ciclo de vida e reconexão

```
           app abre                   rede cai / token expira
              │                              │
              ▼                              ▼
 ┌──────────────────────┐       ┌──────────────────────┐
 │ pull REST            │       │ WS fecha             │
 │ (updatedSince)       │       │ (close code 4002)    │
 └──────────┬───────────┘       └──────────┬───────────┘
            │                              │
            ▼                              ▼
 ┌──────────────────────┐       ┌──────────────────────┐
 │ abre WS              │       │ backoff exponencial  │
 │ (já subscrito auto)  │       │ 1s,2s,4s,...≤30s     │
 └──────────┬───────────┘       └──────────┬───────────┘
            │                              │
            └────────── consumir eventos ──┘
```

- **Backoff (cliente):** `min(1s * 2^attempt, 30s)`, reset em conexão bem
  sucedida.
- **Reconexão pós-pausa do app:** sempre disparar pull REST incremental
  **antes** de processar eventos recebidos depois do reconnect.
- **Duplicação:** o cliente pode receber uma mensagem via WS e novamente via
  pull. A chave primária `id` + `version`/`updatedAt` garantem idempotência
  (last-write-wins).

---

## 6. Close codes

| Code | Nome                 | Quem fecha | Significado                                   |
|------|----------------------|:----------:|-----------------------------------------------|
| 1000 | `NORMAL_CLOSURE`     | ambos      | Encerramento cordial.                         |
| 1009 | `MESSAGE_TOO_BIG`    | server     | Frame > 64 KiB.                               |
| 1011 | `INTERNAL_ERROR`     | server     | Erro no servidor.                             |
| 4001 | `UNAUTHORIZED`       | server     | JWT inválido no handshake.                    |
| 4002 | `TOKEN_EXPIRED`      | server     | JWT expirado em conexão ativa.                |
| 4003 | `TOO_MANY_CONNS`     | server     | Limite de conexões por usuário excedido.      |
| 4004 | `IDLE_TIMEOUT`       | server     | Sem `ping` dentro da janela de 60 s.          |
| 4005 | `CROSS_TENANT`       | server     | Tentativa de `subscribe` em thread de outro condomínio. |
| 4006 | `KICK_BY_ADMIN`      | server     | Sessão revogada (ex.: logout global).         |

Códigos `4xxx` são privados (reservados para aplicação), conforme RFC 6455.

---

## 7. Limitações e quotas

| Limite                                  | Valor sugerido |
|-----------------------------------------|----------------|
| Conexões simultâneas por usuário        | 5              |
| Tamanho máximo de frame (texto)         | 64 KiB         |
| Janela de inatividade (sem `ping`)      | 60 s           |
| Latência p95 entre POST REST e `message.new` | 500 ms    |
| Mensagens `subscribe`/`unsubscribe` por minuto | 30     |

Exceder qualquer quota resulta em `error` (não fatal) ou close com `1008`
(`POLICY_VIOLATION`) dependendo da severidade.

---

## 8. Fluxo de envio (cliente)

O envio continua por REST (`POST /v1/chat-threads/{id}/messages`). O servidor
persiste e, antes de responder, enfileira o evento `message.new` para
**todos** os participantes da thread (inclusive o remetente). A resposta
REST contém o DTO canônico, que o cliente persiste localmente. Quando o
evento WS chegar depois, o `upsert` é idempotente (mesmo `id`, mesmo
`updatedAt`).

```
Cliente A                  Backend                      Cliente B
    |                          |                            |
    |  POST /.../messages ---->|                            |
    |                          |-- persist msg              |
    |                          |-- emit message.new ------->|
    |<----- 200 ChatMessage ---|                            |
    |                          |-- emit message.new ---(A)  |
    |<----- WS message.new ----|  (no-op upsert)            |
```

Se o cliente A estiver offline, ele só persiste localmente (outbox) e, ao
voltar online, envia o POST — o evento WS é emitido nesse momento. O campo
`clientRefId` (em `SendMessageRequestDto`) deve ser a mesma string que o
outbox usa como `Idempotency-Key`.

---

## 9. Testes (servidor)

Cenários mínimos para validar a implementação do gateway:

- [ ] Handshake com token válido → 101 Switching Protocols.
- [ ] Handshake sem token → 401.
- [ ] Handshake com token de outro condomínio não vaza eventos.
- [ ] `POST /v1/chat-threads/{id}/messages` emite `message.new` para todos os
      participantes em menos de 500 ms (p95).
- [ ] `subscribe` com thread que o usuário não participa → `error` com
      `FORBIDDEN` e sessão permanece aberta.
- [ ] Conexão sem `ping` por 60 s é fechada com `4004`.
- [ ] Token expirado em conexão ativa fecha com `4002`.
- [ ] Conexão #6 do mesmo usuário fecha com `4003`.
- [ ] Reconexão + pull REST `updatedSince` traz mensagens perdidas sem
      duplicar.

---

## 10. Implementação atual no cliente

- Plugin Ktor `WebSockets` instalado em `HttpClientFactory.kt` (ping de
  keep-alive a cada 20 s).
- Classe `ChatRealtimeClient` (`data/remote/ws/`) abre a sessão no
  boot do `RemoteChatRepository`, envia heartbeat JSON a cada 30 s e emite
  eventos tipados (`ChatRealtimeEvent.MessageNew`, `ThreadUpdate`).
- Os eventos alimentam `persistMessage`/`persistThread`, que atualizam o
  SQLDelight local — a UI reage via o Flow existente das queries.
- REST continua sendo usado para histórico (`listThreads`, `listMessages`),
  envio (`sendMessage`) e pull incremental no `observeThreads`/`observeMessages`.
