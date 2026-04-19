# Grupo de chat do condomínio (thread singleton)

Contrato do recurso `chat-threads` de tipo `CONDO_GROUP` — uma conversa
**coletiva e singleton** por condomínio, onde todos os membros ativos
(RESIDENT, SUPERVISOR, ADMIN) participam automaticamente. Usada pelo app
como canal "mural" de comunicação.

Complementa o [`README.md`](./README.md) (§10), o
[`CHAT_WEBSOCKET.md`](./CHAT_WEBSOCKET.md) (push de eventos) e o
[`openapi.yaml`](./openapi.yaml) (schemas `ChatThread` e `ChatThreadKind`).

---

## 1. Princípios

- **Singleton por condomínio.** Há **uma, e apenas uma**, thread com
  `kind = 'CONDO_GROUP'` por condomínio. Unicidade deve ser garantida no
  banco: `UNIQUE (condominium_id) WHERE kind = 'CONDO_GROUP'`.
- **Membership automático.** `participantUserIds` = todos os usuários
  ativos (não deletados) do condomínio. O cliente **nunca** faz POST/PATCH
  para alterar a lista. O servidor sincroniza on-the-fly (hooks de
  `INSERT/UPDATE/DELETE` na tabela `users`).
- **Idempotente.** `GET /v1/condominiums/{id}/chat-threads/group` cria
  na primeira chamada e sempre retorna a mesma thread depois.
- **Reuso total dos demais contratos.** Envio de mensagens, listagem,
  WebSocket, outbox, `Idempotency-Key` — tudo funciona igual ao chat
  direto. A única diferença é `kind = 'CONDO_GROUP'` no DTO.

---

## 2. Endpoint

```
GET /v1/condominiums/{id}/chat-threads/group
```

| Campo       | Tipo | Obrigatório | Notas                                      |
|-------------|------|-------------|--------------------------------------------|
| `id` (path) | UUID | Sim         | Condomínio. Deve bater com `session.condominiumId`. |

Headers:

- `Authorization: Bearer <accessToken>` — obrigatório.

### 2.1 Comportamento

1. Se já existir thread com `(condominium_id = :id, kind = 'CONDO_GROUP')`,
   retorna-a.
2. Caso contrário, cria em transação:
   - `kind = 'CONDO_GROUP'`
   - `title = 'Grupo do condomínio'`
   - `participantUserIds` = `SELECT id FROM users WHERE condominium_id = :id AND deleted_at IS NULL`
   - `version = 0`, `created_at = now()`, `updated_at = now()`
3. Retorna o `ChatThread` (schema padrão, ver `openapi.yaml`).

### 2.2 Resposta

```json
{
  "id": "9c7a1b0e-2f8d-4f66-a2b4-1c3e5d8a9f01",
  "condominiumId": "b01f3f20-5ec0-4f4a-9d44-2e1a6bf8a3f0",
  "title": "Grupo do condomínio",
  "participantUserIds": [
    "dec0d3ad-0000-0000-0000-000000000001",
    "dec0d3ad-0000-0000-0000-000000000002",
    "..."
  ],
  "lastMessagePreview": null,
  "lastMessageAt": null,
  "updatedAt": "2026-04-18T10:00:00Z",
  "version": 0,
  "deleted": false,
  "kind": "CONDO_GROUP"
}
```

### 2.3 Erros

| HTTP | `error.code`     | Quando                                                  |
|------|------------------|---------------------------------------------------------|
| 401  | `TOKEN_EXPIRED`  | Access token expirado.                                  |
| 403  | `CROSS_TENANT`   | `{id}` diferente de `session.condominiumId`.            |
| 404  | `NOT_FOUND`      | Condomínio inexistente.                                 |

---

## 3. Sincronização de membership

Sempre que um usuário entra ou sai do condomínio, o servidor **deve**:

1. Atualizar `participantUserIds` da thread `CONDO_GROUP`.
2. Incrementar `version` e atualizar `updated_at = now()`.
3. Emitir evento WebSocket `thread.update` (ver `CHAT_WEBSOCKET.md` §4.2)
   com o DTO completo. Todos os clientes conectados recebem.

Gatilhos típicos (todos devem propagar a membership):

| Operação do usuário                               | Ação no grupo                           |
|---------------------------------------------------|------------------------------------------|
| `POST /auth/register-condominium` (novo ADMIN)    | Cria grupo (se ainda não existir) e inclui o ADMIN. |
| `POST /auth/join-condominium` (novo RESIDENT)     | Adiciona em `participantUserIds`.       |
| `POST /units/{unitId}/members` (novo RESIDENT)    | Adiciona em `participantUserIds`.       |
| `PATCH /users/{id}` muda `condominiumId`          | Remove do grupo antigo, adiciona no novo. |
| Soft-delete de usuário (tombstone)                | Remove de `participantUserIds`.         |

Recomendação: use um **trigger Postgres** ou um **outbox de domínio** para
garantir que a atualização aconteça na mesma transação do evento-fonte.

---

## 4. Envio de mensagens

Sem novidade: o cliente usa o mesmo endpoint:

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"text":"Bom dia a todos!"}' \
  "https://api.meucondominio.app/v1/chat-threads/$GROUP_ID/messages"
```

Todos os membros em `participantUserIds` recebem `message.new` via WS
(quando conectados). Clientes offline puxam via pull incremental
(`since=<lastSyncAt>`).

Permissão: qualquer papel com `CHAT_PARTICIPATE` (todos). Se, no futuro,
for preciso restringir quem pode postar (ex.: só ADMIN), adicionar um
campo `postPermission` ao `ChatThread` — fora do escopo do MVP.

---

## 5. Autorização

- **Leitura de mensagens e thread:** qualquer usuário do mesmo
  condomínio (`condominiumId` bate). Não é preciso checar
  `participantUserIds` para o grupo — membership é implícita.
- **Envio:** qualquer usuário do mesmo condomínio.
- **Cross-tenant:** rejeitar como `403 CROSS_TENANT` se o solicitante
  tentar acessar `GROUP_ID` de outro condomínio.

---

## 6. Consulta de referência (Postgres)

### 6.1 Tabela `chat_threads` (ajuste sugerido)

```sql
ALTER TABLE chat_threads
  ADD COLUMN kind TEXT NOT NULL DEFAULT 'DIRECT'
  CHECK (kind IN ('DIRECT', 'CONDO_GROUP'));

CREATE UNIQUE INDEX chat_threads_condo_group_uidx
  ON chat_threads (condominium_id)
  WHERE kind = 'CONDO_GROUP' AND deleted_at IS NULL;
```

### 6.2 Ensure-or-create (pseudo)

```sql
WITH found AS (
  SELECT * FROM chat_threads
  WHERE condominium_id = :condoId AND kind = 'CONDO_GROUP' AND deleted_at IS NULL
), created AS (
  INSERT INTO chat_threads (id, condominium_id, title, kind, participants, version, created_at, updated_at)
  SELECT gen_random_uuid(), :condoId, 'Grupo do condomínio', 'CONDO_GROUP',
         ARRAY(SELECT id FROM users WHERE condominium_id = :condoId AND deleted_at IS NULL),
         0, now(), now()
  WHERE NOT EXISTS (SELECT 1 FROM found)
  RETURNING *
)
SELECT * FROM found UNION ALL SELECT * FROM created;
```

### 6.3 Trigger de membership

```sql
CREATE OR REPLACE FUNCTION chat_group_sync_members() RETURNS trigger AS $$
BEGIN
  UPDATE chat_threads
  SET participants = ARRAY(
        SELECT id FROM users
        WHERE condominium_id = NEW.condominium_id AND deleted_at IS NULL
      ),
      version = version + 1,
      updated_at = now()
  WHERE condominium_id = NEW.condominium_id AND kind = 'CONDO_GROUP';
  -- enfileirar emit de thread.update pelo gateway WS
  PERFORM pg_notify('chat_thread_update', NEW.condominium_id::text);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_sync_condo_group
  AFTER INSERT OR UPDATE OF condominium_id, deleted_at OR DELETE ON users
  FOR EACH ROW EXECUTE FUNCTION chat_group_sync_members();
```

---

## 7. Uso do lado do cliente

- `domain/model/Chat.kt` → `ChatThread.kind: ChatThreadKind` (`DIRECT` |
  `CONDO_GROUP`).
- `data/remote/dto/Dtos.kt` → `ChatThreadDto.kind: String = "DIRECT"`.
- `data/remote/ApiServices.kt` → `ChatApiService.ensureCondoGroup(condoId)`
  chama `GET /condominiums/{id}/chat-threads/group`.
- `data/repository/remote/RemoteChatRepository.kt` → `ensureCondoGroup`
  consulta local primeiro; se online, chama a API, remove placeholder
  local (se id mudou) e persiste o DTO autoritativo.
- `presentation/features/chat/ChatThreadsViewModel.kt` → ao entrar na
  tela, chama `chat.ensureCondoGroup(condoId)` em paralelo ao
  `users.refresh(condoId)`.
- `presentation/features/chat/ChatThreadsScreen.kt` → pina o card do
  grupo no topo da seção **Conversas** com ícone de grupo e badge
  "Grupo".

---

## 8. Check-list para o backend

- [ ] Coluna `kind` em `chat_threads` com CHECK e default `'DIRECT'`.
- [ ] Índice único parcial para garantir singleton por condomínio.
- [ ] Endpoint `GET /v1/condominiums/{id}/chat-threads/group` idempotente.
- [ ] Bootstrapping: ao criar um condomínio (`register-condominium`)
      criar também a thread do grupo na mesma transação.
- [ ] Trigger/outbox que recalcula `participants` em qualquer
      INSERT/UPDATE/DELETE em `users` do condomínio.
- [ ] Emissão de `thread.update` no WS ao alterar membership.
- [ ] `POST /condominiums/{condoId}/chat-threads` **deve** rejeitar
      `kind == 'CONDO_GROUP'` no body (essa thread só é criável pelo
      endpoint dedicado).
- [ ] Tenancy: rejeitar acesso cross-condomínio com `403 CROSS_TENANT`.
- [ ] Listagem padrão `GET /condominiums/{condoId}/chat-threads` inclui
      a thread do grupo (mesmo que o usuário acabou de ingressar).
- [ ] Testes: happy path, cross-tenant, novo membro é adicionado ao
      grupo imediatamente, soft-delete remove do grupo, WS `thread.update`
      disparado em < 500 ms (p95).
