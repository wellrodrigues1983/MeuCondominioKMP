# Listagem de membros do condomínio

Este documento é o contrato completo do endpoint `GET /v1/condominiums/{id}/members`,
usado pelo app **Meu Condomínio** para popular a lista de contatos disponíveis
para iniciar uma conversa de chat. É complemento do [`README.md`](./README.md)
e do `openapi.yaml` (esquemas reutilizados aqui).

O app chama esse endpoint a partir de `RemoteUserDirectory.refresh()` e guarda
o resultado em SQLDelight (`users` table) para consumo offline. A `ChatThreadsScreen`
usa esses dados na seção "Iniciar conversa".

---

## 1. Princípios

- **Fonte de verdade para contatos.** Sem esse endpoint, o cliente não consegue
  descobrir com quem pode abrir um chat quando ainda não existe uma thread.
- **Tenancy rigorosa.** O servidor **deve** rejeitar `{id}` diferente de
  `session.condominiumId` com `403 CROSS_TENANT`.
- **Autorização ampla.** Todos os papéis (`ADMIN`, `SUPERVISOR`, `RESIDENT`)
  podem listar — é a mesma permissão de `CHAT_PARTICIPATE`. A visibilidade é
  controlada no cliente conforme a matriz em §4.
- **Compatível com sync incremental.** Suporta `updatedSince` + paginação por
  cursor, idêntico aos demais endpoints de listagem (ver §4 do README).
- **Tombstones.** Usuários desligados do condomínio (`deleted_at IS NOT NULL`)
  aparecem com `deleted: true` se passarem pelo filtro `updatedSince`.

---

## 2. Endpoint

```
GET /v1/condominiums/{id}/members
```

| Campo           | Tipo     | Obrigatório | Notas                                                    |
|-----------------|----------|-------------|----------------------------------------------------------|
| `id` (path)     | UUID     | Sim         | Condomínio. Deve ser igual ao `condominiumId` do token.  |
| `role`          | string   | Não         | Filtro por papel: `ADMIN`, `SUPERVISOR`, `RESIDENT`.     |
| `updatedSince`  | ISO-8601 | Não         | Sync incremental (ver §3.2 do README).                   |
| `cursor`        | string   | Não         | Cursor opaco da página anterior.                         |
| `limit`         | integer  | Não         | Default 50, máx 200.                                     |

Headers:

- `Authorization: Bearer <accessToken>` — obrigatório.

---

## 3. Resposta

### 3.1 Sucesso (`200 OK`)

```json
{
  "items": [
    {
      "id": "7d0b9a74-1b4e-4c1a-b3de-4e8c5b0e71b6",
      "name": "Maria Silva",
      "email": "maria@exemplo.com",
      "phone": "+5511999998888",
      "role": "RESIDENT",
      "condominiumId": "b01f3f20-5ec0-4f4a-9d44-2e1a6bf8a3f0",
      "unitId": "ab4b2f12-b04d-4e8c-8e3a-6c0b9f7fd1a0",
      "avatarUrl": "https://cdn.meucondominio.app/avatars/7d0b9a74.jpg",
      "createdAt": "2026-03-10T09:45:00Z",
      "updatedAt": "2026-04-12T18:20:00Z",
      "version": 4,
      "deleted": false
    },
    {
      "id": "dec0d3ad-0000-0000-0000-000000000001",
      "updatedAt": "2026-04-14T11:02:00Z",
      "version": 7,
      "deleted": true
    }
  ],
  "nextCursor": "eyJpZCI6Ii4uLiIsInQiOiIuLi4ifQ"
}
```

- Ordenação **obrigatória**: `updatedAt ASC, id ASC` (mesma regra da §3.2 do
  README). Desempate estável é crítico para o cliente não pular linhas.
- Tombstones carregam apenas `id`, `updatedAt`, `version` e `deleted: true` —
  o cliente apaga a linha local por `id`.
- `nextCursor: null` quando chega ao fim da coleção.

### 3.2 Envelope de erro

Formato padrão (ver §7 do README). Códigos esperados:

| HTTP | `error.code`     | Quando                                                     |
|------|------------------|------------------------------------------------------------|
| 401  | `TOKEN_EXPIRED`  | Access token expirado — cliente aciona `/auth/refresh`.    |
| 403  | `CROSS_TENANT`   | `{id}` diferente de `session.condominiumId`.               |
| 404  | `NOT_FOUND`      | Condomínio inexistente.                                    |
| 422  | `VALIDATION_FAILED` | `role` inválido. `details.fields.role` descreve o erro. |

---

## 4. Regras de visibilidade

O servidor **retorna todos os membros ativos** do condomínio (filtrados por
`role` se informado). O filtro de visibilidade por papel é feito no cliente:

| Papel do solicitante | Vê                                      |
|----------------------|------------------------------------------|
| `RESIDENT`           | `ADMIN`, `SUPERVISOR`                    |
| `SUPERVISOR`         | `ADMIN`, `SUPERVISOR`, `RESIDENT`        |
| `ADMIN`              | `ADMIN`, `SUPERVISOR`, `RESIDENT`        |

> Observação: o cliente também pode mandar `?role=SUPERVISOR` ou
> `?role=ADMIN` para reduzir payload em redes ruins. Sempre exclui o próprio
> usuário localmente (`it.id != session.userId`).

---

## 5. Consulta de referência (Postgres)

```sql
SELECT u.*
FROM users u
WHERE u.condominium_id = :condoId
  AND (:role IS NULL OR u.role = :role)
  AND (
    :updatedSince IS NULL
    OR COALESCE(u.deleted_at, u.updated_at) >= :updatedSince
  )
  AND (
    :cursor IS NULL
    OR (COALESCE(u.deleted_at, u.updated_at), u.id) > (:cursorUpdatedAt, :cursorId)
  )
ORDER BY COALESCE(u.deleted_at, u.updated_at) ASC, u.id ASC
LIMIT :limit;
```

Índice recomendado:

```sql
CREATE INDEX users_condo_sync_idx
  ON users (condominium_id, COALESCE(deleted_at, updated_at), id);
```

---

## 6. Exemplos curl

### 6.1 Todos os membros (primeira página)

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://api.meucondominio.app/v1/condominiums/$CID/members?limit=100"
```

### 6.2 Apenas staff

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://api.meucondominio.app/v1/condominiums/$CID/members?role=SUPERVISOR"
```

### 6.3 Sync incremental (a partir do último `updatedAt` conhecido)

```bash
curl -H "Authorization: Bearer $TOKEN" \
  "https://api.meucondominio.app/v1/condominiums/$CID/members?updatedSince=2026-04-15T00:00:00Z&limit=200"
```

---

## 7. Uso do lado do cliente

- `data/remote/ApiServices.kt` → `CondominiumApiService.listMembers(condoId, role)`:

  ```kotlin
  suspend fun listMembers(condominiumId: String, role: String? = null): List<UserDto> =
      http.get("condominiums/$condominiumId/members") { role?.let { parameter("role", it) } }
          .body<PageDto<UserDto>>().items
  ```

- `data/repository/remote/RemoteUserDirectory.kt` chama esse método em
  `refresh(condominiumId)` e upserta cada `UserDto` na tabela `users` local
  via `userQueries.upsertUser(...)`.
- `presentation/features/chat/ChatThreadsViewModel.kt` observa
  `users.observeUsers(u.condominiumId)` e aplica o filtro da §4 antes de
  renderizar a seção "Iniciar conversa".

---

## 8. Check-list para o backend

- [ ] Rota `GET /v1/condominiums/{id}/members` registrada na feature **Users**.
- [ ] Middleware de tenancy rejeita `{id} != session.condominiumId` com `403
      CROSS_TENANT` **antes** de executar a query.
- [ ] Query com `ORDER BY COALESCE(deleted_at, updated_at) ASC, id ASC` e
      índice equivalente criado.
- [ ] Tombstones (`deleted: true`) incluídos quando `updatedSince` é informado.
- [ ] Resposta usa o mesmo wrapper `Page` dos demais endpoints (`items`,
      `nextCursor`).
- [ ] Filtro `role` validado contra o enum `UserRole`; inválido → `422`.
- [ ] `limit` aceita 1..200, default 50.
- [ ] Incluído no `openapi.yaml` e no arquivo de testes de integração
      (pelo menos: happy path, tenancy, sync incremental com tombstone).
- [ ] Métrica: latência p95 < 150 ms com 500 usuários/condomínio.
