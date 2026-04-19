# Chat WebSocket — guia de implementação (Spring Boot + Java 25)

Este documento é o **guia prático** para implementar o gateway WebSocket
descrito em [`CHAT_WEBSOCKET.md`](./CHAT_WEBSOCKET.md) usando a stack do
backend (Spring Boot 3.x + Java 25). É código concreto, não spec —
o contrato (frames, close codes, payloads) fica em `CHAT_WEBSOCKET.md` e
deve ser seguido à risca.

---

## 0. Stack assumida

- **Java:** 25 (virtual threads via `spring.threads.virtual.enabled=true`)
- **Spring Boot:** 3.x
- **Spring WebSocket:** nativo (não STOMP — o cliente envia/recebe JSON cru)
- **JSON:** Jackson (padrão do Spring)
- **JWT:** mesma lib usada nas rotas REST (Nimbus, Auth0, etc.)
- **Escala inicial:** 1 instância (registry em memória)
- **Escala ≥2 instâncias:** Redis pub/sub (§9)

> Não use `@EnableWebSocketMessageBroker` / STOMP: o cliente Ktor
> do app manda `{"type":"ping"}` / `{"type":"subscribe"}` como JSON livre.
> STOMP exigiria mudar o cliente e não traz benefício pra esse volume.

---

## 1. Dependência

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

---

## 2. Estrutura sugerida

```
chat/
├── ws/
│   ├── ChatWebSocketConfig.java
│   ├── JwtHandshakeInterceptor.java
│   ├── ChatWebSocketHandler.java
│   ├── SessionRegistry.java
│   └── WsEnvelope.java
├── ChatMessageService.java     ← já existe; ganha hooks afterCommit
└── ChatThreadService.java      ← já existe; idem
```

---

## 3. Configuração

```java
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ChatWebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler handler;
    private final JwtService jwt;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/v1/ws/chat")
                .addInterceptors(new JwtHandshakeInterceptor(jwt))
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public ServletServerContainerFactoryBean wsContainer() {
        var c = new ServletServerContainerFactoryBean();
        c.setMaxTextMessageBufferSize(64 * 1024);   // contract: 64 KiB
        c.setMaxBinaryMessageBufferSize(0);          // binário não é usado
        c.setMaxSessionIdleTimeout(60_000L);         // IDLE_TIMEOUT (60s)
        return c;
    }
}
```

---

## 4. Handshake + autenticação

O cliente manda o JWT no query string (`?token=<accessToken>`). Se
inválido, responda **401 no upgrade** — o cliente Ktor trata como
`UNAUTHORIZED` (close code `4001`).

```java
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwt;
    public JwtHandshakeInterceptor(JwtService jwt) { this.jwt = jwt; }

    @Override
    public boolean beforeHandshake(ServerHttpRequest req, ServerHttpResponse res,
                                   WebSocketHandler h, Map<String, Object> attrs) {
        var token = UriComponentsBuilder.fromUri(req.getURI())
                .build().getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            res.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            var claims = jwt.verify(token);
            attrs.put("userId",  claims.userId().toString());
            attrs.put("condoId", claims.condoId().toString());
            attrs.put("role",    claims.role().name());
            return true;
        } catch (Exception e) {
            res.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest r, ServerHttpResponse s,
                               WebSocketHandler h, Exception e) {}
}
```

> Se o JWT expirar **durante** uma sessão ativa, você precisa de um
> verificador periódico (scheduler por sessão) que chame
> `session.close(new CloseStatus(4002, "TOKEN_EXPIRED"))`. Para o MVP
> pode pular — o cliente reconecta por outros motivos de qualquer jeito.

---

## 5. Registro de sessões

`WebSocketSession.sendMessage(...)` **não é thread-safe**. Chamadas
concorrentes ao mesmo socket precisam de `synchronized(session)`.

```java
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    // userId -> sessões abertas (um usuário pode ter app + web + etc.)
    private final Map<String, Set<WebSocketSession>> byUser = new ConcurrentHashMap<>();

    public void add(String userId, WebSocketSession s) {
        byUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(s);
    }

    public void remove(String userId, WebSocketSession s) {
        var set = byUser.get(userId);
        if (set == null) return;
        set.remove(s);
        if (set.isEmpty()) byUser.remove(userId);
    }

    public int sessionsOf(String userId) {
        return byUser.getOrDefault(userId, Set.of()).size();
    }

    public void sendToUsers(Collection<String> userIds, String payload) {
        var msg = new TextMessage(payload);
        for (var uid : userIds) {
            var sessions = byUser.get(uid);
            if (sessions == null) continue;
            for (var s : sessions) {
                if (!s.isOpen()) continue;
                try {
                    synchronized (s) { s.sendMessage(msg); }
                } catch (IOException e) {
                    log.warn("Falha ao enviar para {}: {}", uid, e.getMessage());
                    // a sessão será removida em afterConnectionClosed
                }
            }
        }
    }
}
```

---

## 6. Handler

```java
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final int MAX_CONN_PER_USER = 5;   // contract §7

    private final SessionRegistry registry;
    private final ObjectMapper json;

    @Override
    public void afterConnectionEstablished(WebSocketSession s) throws Exception {
        var userId = (String) s.getAttributes().get("userId");
        if (registry.sessionsOf(userId) >= MAX_CONN_PER_USER) {
            s.close(new CloseStatus(4003, "TOO_MANY_CONNS"));
            return;
        }
        registry.add(userId, s);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
        var userId = (String) s.getAttributes().get("userId");
        if (userId != null) registry.remove(userId, s);
    }

    @Override
    protected void handleTextMessage(WebSocketSession s, TextMessage message) throws Exception {
        JsonNode root;
        try {
            root = json.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(s, "VALIDATION_FAILED", "invalid JSON");
            return;
        }
        switch (root.path("type").asText()) {
            case "ping" -> send(s, "{\"type\":\"pong\"}");
            case "subscribe", "unsubscribe" -> {
                // opcional — cliente atual não envia. Ignorar silenciosamente.
            }
            default -> sendError(s, "VALIDATION_FAILED", "unknown type");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession s, Throwable ex) throws Exception {
        log.warn("transport error: {}", ex.getMessage());
        if (s.isOpen()) s.close(CloseStatus.SERVER_ERROR);
    }

    private void send(WebSocketSession s, String payload) throws IOException {
        synchronized (s) { s.sendMessage(new TextMessage(payload)); }
    }

    private void sendError(WebSocketSession s, String code, String msg) throws IOException {
        send(s, json.writeValueAsString(Map.of(
            "type", "error",
            "error", Map.of("code", code, "message", msg)
        )));
    }
}
```

---

## 7. Emitindo `message.new` e `thread.update`

### 7.1 Envelope

```java
public record WsEnvelope(String type, Object payloadKey, Object payload) {

    /** Serializa como { "type": "...", "<payloadKey>": <payload> } */
    public Map<String, Object> asMap() {
        return Map.of("type", type, payloadKey.toString(), payload);
    }
}
```

### 7.2 `ChatMessageService.send(...)`

**Regra de ouro:** emitir **apenas após commit**. Se a transação der
rollback e o evento já foi pro ar, os clientes recebem mensagem fantasma.

```java
@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository repo;
    private final ChatThreadRepository threads;
    private final SessionRegistry registry;
    private final ObjectMapper json;

    @Transactional
    public ChatMessageDto send(UUID threadId, UUID senderId, SendMessageRequestDto body) {
        // 1. Idempotência via clientRefId
        if (body.clientRefId() != null) {
            var existing = repo.findById(body.clientRefId());
            if (existing.isPresent()) return ChatMessageDto.from(existing.get());
        }

        // 2. Persiste (id = clientRefId se enviado, senão UUID novo)
        var id = body.clientRefId() != null ? body.clientRefId() : UUID.randomUUID();
        var now = Instant.now();
        var msg = new ChatMessage(id, threadId, senderId, body.text(), now, now, 0, false);
        repo.save(msg);

        // 3. Atualiza lastMessageAt/Preview da thread (última versão do DTO)
        var thread = threads.touchLastMessage(threadId, body.text(), now);

        var msgDto    = ChatMessageDto.from(msg);
        var threadDto = ChatThreadDto.from(thread);

        // 4. Emite pós-commit
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                emit("message.new", "message", msgDto, thread.participantUserIds());
                emit("thread.update", "thread",  threadDto, thread.participantUserIds());
            }
        });
        return msgDto;
    }

    private void emit(String type, String key, Object value, Collection<UUID> recipients) {
        try {
            var payload = json.writeValueAsString(Map.of("type", type, key, value));
            registry.sendToUsers(
                recipients.stream().map(UUID::toString).toList(),
                payload
            );
        } catch (Exception e) {
            // logar e seguir — WS é best-effort; o cliente faz pull
        }
    }
}
```

### 7.3 `ChatThreadService`

Emitir `thread.update` em:

- `create(...)` — todos os `participantUserIds` (inclui o criador).
- `addMember(...) / removeMember(...)` — todos os `participantUserIds`
  após a mudança (mais, opcionalmente, o removido — pra ele saber que
  saiu).
- `softDelete(...)` — todos os `participantUserIds`; payload com
  `"deleted": true`.

Para `CONDO_GROUP`, `participantUserIds` precisa estar consistente (ou
calculado na hora via `userRepo.findActiveIdsByCondoId(condoId)`).

---

## 8. DTOs (exatamente os do REST)

Os `ChatMessageDto` e `ChatThreadDto` do WebSocket **devem** ser
idênticos aos do REST (mesmo Jackson, mesmas anotações). Garanta que:

- `sentAt` e `updatedAt` serializem em ISO-8601 UTC com `Z` no final.
- `deleted` é `boolean`, não `int`.
- `participantUserIds` é um array de UUIDs string.
- `kind` é `"DIRECT"` ou `"CONDO_GROUP"`.

Exemplo de config global:

```java
@Bean
public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
    return b -> b.serializers(new InstantSerializer())   // emite "Z"
                 .timeZone(TimeZone.getTimeZone("UTC"))
                 .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
}
```

---

## 9. Escala além de 1 instância

Com `N` instâncias, o registry em memória não enxerga sessões das
outras. Padrão mais simples:

```
┌─ Instância A ─┐        ┌──────────────┐        ┌─ Instância B ─┐
│ ChatService   │──PUB──▶│  Redis       │──SUB──▶│ Listener      │
│ Registry(A)   │        │  Pub/Sub     │        │ Registry(B)   │
└─ WS clients ──┘        └──────────────┘        └─ WS clients ──┘
```

### 9.1 Publisher

```java
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final StringRedisTemplate redis;
    private final SessionRegistry local;
    private final ObjectMapper json;

    public void publish(Collection<String> userIds, String payload) {
        // Entrega local (usa a conexão TCP já aberta se existir na mesma instância)
        local.sendToUsers(userIds, payload);
        // E publica no Redis pra outras instâncias replicarem para *seus* clients
        try {
            var envelope = json.writeValueAsString(new FanoutEnvelope(userIds, payload));
            redis.convertAndSend("chat.fanout", envelope);
        } catch (JsonProcessingException ignored) {}
    }

    public record FanoutEnvelope(Collection<String> userIds, String payload) {}
}
```

### 9.2 Listener

```java
@Configuration
public class ChatRedisConfig {

    @Bean
    RedisMessageListenerContainer container(RedisConnectionFactory cf,
                                            ChatFanoutListener listener) {
        var c = new RedisMessageListenerContainer();
        c.setConnectionFactory(cf);
        c.addMessageListener(listener, new ChannelTopic("chat.fanout"));
        return c;
    }
}

@Component
@RequiredArgsConstructor
public class ChatFanoutListener implements MessageListener {
    private final SessionRegistry local;
    private final ObjectMapper json;

    @Override
    public void onMessage(Message msg, byte[] pattern) {
        try {
            var env = json.readValue(msg.getBody(), ChatEventPublisher.FanoutEnvelope.class);
            local.sendToUsers(env.userIds(), env.payload());   // entrega local só
        } catch (Exception ignored) {}
    }
}
```

`ChatMessageService.emit(...)` passa a chamar `publisher.publish(...)`
em vez de `registry.sendToUsers(...)`. Cada instância entrega apenas
pros sockets que ela mesma possui — o loop não duplica porque `local`
dentro do listener **não** re-publica no Redis.

---

## 10. Virtual threads (Java 25)

Em `application.yml`:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Com isso, cada sessão WS que está bloqueada em `incoming.read()` custa
poucos KB de heap em vez de ocupar uma platform thread inteira. Numa
instância 4 vCPU / 4 GiB dá pra segurar tranquilamente 10k+ conexões
simultâneas sem tuning extra.

Evite `@Async` na emissão — o fan-out já é rápido e inserir um thread
pool no meio só adiciona latência e risco de bugs.

---

## 11. Checklist de implementação

- [ ] `starter-websocket` no `pom.xml`.
- [ ] `ChatWebSocketConfig` registrando `/v1/ws/chat`.
- [ ] `JwtHandshakeInterceptor` — 401 no upgrade se token inválido.
- [ ] `SessionRegistry` com `synchronized(session)` no send.
- [ ] `ChatWebSocketHandler` responde `pong` a `ping`, fecha `4003` se
      6+ conexões do mesmo usuário, ignora `subscribe/unsubscribe`.
- [ ] `ChatMessageService.send(...)` emite `message.new` **em afterCommit**.
- [ ] `ChatThreadService.create/update/softDelete` emitem `thread.update`
      **em afterCommit**.
- [ ] DTOs do WS são **os mesmos** do REST (Instant em ISO-8601 com `Z`).
- [ ] `CONDO_GROUP`: `participantUserIds` sempre espelha membros ativos
      do condomínio (trigger ou recompute no service).
- [ ] `maxSessionIdleTimeout = 60s` em `ServletServerContainerFactoryBean`.
- [ ] `spring.threads.virtual.enabled=true` em `application.yml`.
- [ ] Quando escalar pra ≥2 instâncias, adicionar `ChatEventPublisher` +
      Redis pub/sub.

---

## 12. Testes mínimos (JUnit 5 + `WebSocketClient`)

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ChatWebSocketIntegrationTest {

    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired ChatMessageService svc;

    @Test
    void messageNewReachesParticipant() throws Exception {
        var token = jwt.issue(userAId, condoId);
        var received = new CompletableFuture<String>();
        var client = new StandardWebSocketClient();
        var session = client.execute(new TextWebSocketHandler() {
            @Override protected void handleTextMessage(WebSocketSession s, TextMessage m) {
                received.complete(m.getPayload());
            }
        }, "ws://localhost:" + port + "/v1/ws/chat?token=" + token).get();

        // ACT: outro usuário manda mensagem na thread onde userA participa
        svc.send(threadId, userBId, new SendMessageRequestDto("oi", null));

        var frame = received.get(3, SECONDS);
        assertThat(frame).contains("\"type\":\"message.new\"");
        session.close();
    }

    @Test
    void handshakeWithoutTokenReturns401() { /* ... */ }

    @Test
    void overLimitConnectionsGets4003() { /* ... */ }
}
```

---

## 13. Erros comuns

| Sintoma                                        | Causa provável                                              |
|------------------------------------------------|-------------------------------------------------------------|
| Cliente recebe `message.new` mas com `id` diferente do que mandou | Esqueceu de usar `clientRefId` como `id` ao persistir.   |
| Mensagens chegam em ordem errada                | `updatedAt` igual em millis. Use `sentAt` com nanos ou serial id. |
| Cliente vê fantasmas (mensagem que "sumiu")     | Emitiu dentro da transação em vez de `afterCommit`.         |
| WS fecha em 30s sem motivo                      | `maxSessionIdleTimeout` < intervalo de ping (30s). Use 60s. |
| Users recebem eventos de outros condomínios     | Não filtrou por `participantUserIds` / `condominiumId`.     |
| Segundo `sendMessage` lança `IllegalStateException` | Duas threads escrevendo no mesmo socket. Faltou `synchronized`. |

---

## 14. Referências

- Contrato (frames, close codes, payloads): [`CHAT_WEBSOCKET.md`](./CHAT_WEBSOCKET.md)
- Endpoints REST do chat: [`openapi.yaml`](./openapi.yaml) (seção Chat)
- Grupo do condomínio: [`CONDO_GROUP_CHAT.md`](./CONDO_GROUP_CHAT.md)
- Membros visíveis: [`CONDOMINIUM_MEMBERS.md`](./CONDOMINIUM_MEMBERS.md)
- Auth (refresh, headers): [`README.md`](./README.md) §2
