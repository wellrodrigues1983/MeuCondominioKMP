package br.tec.wrcoder.meucondominio.data.remote.ws

import br.tec.wrcoder.meucondominio.core.BuildConfig
import br.tec.wrcoder.meucondominio.core.logging.AppLogger
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.core.storage.AuthTokens
import br.tec.wrcoder.meucondominio.core.storage.TokenStore
import br.tec.wrcoder.meucondominio.data.remote.dto.ChatMessageDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ChatThreadDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class WsRefreshRequest(val refreshToken: String)

@Serializable
private data class WsRefreshResponse(val accessToken: String, val refreshToken: String)

sealed class ChatRealtimeEvent {
    data class MessageNew(val message: ChatMessageDto) : ChatRealtimeEvent()
    data class ThreadUpdate(val thread: ChatThreadDto) : ChatRealtimeEvent()
}

class ChatRealtimeClient(
    private val http: HttpClient,
    private val tokens: TokenStore,
    private val network: NetworkMonitor,
    private val json: Json,
) {
    private val log = AppLogger.withTag("ChatWS")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _events = MutableSharedFlow<ChatRealtimeEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val events: SharedFlow<ChatRealtimeEvent> = _events.asSharedFlow()

    private var loopJob: Job? = null
    private val refreshMutex = Mutex()

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch { runLoop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    private suspend fun runLoop() {
        var attempt = 0
        while (scope.isActive) {
            val token = tokens.read()?.accessToken
            if (token.isNullOrBlank() || !network.isOnline.value) {
                delay(backoff(++attempt))
                continue
            }
            val outcome = runCatching { connectOnce(token) }
            val needsRefresh = outcome.fold(
                onSuccess = { reason -> reason?.code?.toInt() == AUTH_CLOSE_CODE },
                onFailure = { t ->
                    val msg = t.message.orEmpty()
                    log.i { "disconnected: $msg" }
                    msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true)
                },
            )
            if (needsRefresh) {
                log.i { "auth-related close; refreshing token before reconnect" }
                val refreshed = refreshMutex.withLock { refreshAccessToken() }
                if (refreshed != null) {
                    attempt = 0
                    continue
                }
            } else if (outcome.isSuccess) {
                attempt = 0
            }
            delay(backoff(++attempt))
        }
    }

    private suspend fun connectOnce(token: String): CloseReason? {
        val url = buildWsUrl(token)
        log.i { "connecting" }
        val session = http.webSocketSession(url)
        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                handleText(frame.readText())
            }
        } finally {
            runCatching { session.close() }
        }
        val reason = runCatching { session.closeReason.await() }.getOrNull()
        if (reason != null) log.i { "closed code=${reason.code} message=${reason.message}" }
        return reason
    }

    private suspend fun refreshAccessToken(): String? {
        val current = tokens.read() ?: return null
        if (current.refreshToken.isBlank()) return null
        return try {
            val response: WsRefreshResponse = http
                .post("${BuildConfig.API_BASE_URL.trimEnd('/')}/auth/refresh") {
                    header(HttpHeaders.Authorization, "")
                    contentType(ContentType.Application.Json)
                    setBody(WsRefreshRequest(current.refreshToken))
                }.body()
            val fresh = AuthTokens(response.accessToken, response.refreshToken)
            tokens.updateTokens(fresh)
            http.authProvider<BearerAuthProvider>()?.clearToken()
            fresh.accessToken
        } catch (t: Throwable) {
            log.w(t) { "refresh failed" }
            null
        }
    }

    private suspend fun handleText(text: String) {
        val root: JsonObject = runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrNull() ?: return
        when (root["type"]?.jsonPrimitive?.content) {
            "message.new" -> root["message"]
                ?.let { runCatching { json.decodeFromJsonElement(ChatMessageDto.serializer(), it) }.getOrNull() }
                ?.let { _events.emit(ChatRealtimeEvent.MessageNew(it)) }
            "thread.update" -> root["thread"]
                ?.let { runCatching { json.decodeFromJsonElement(ChatThreadDto.serializer(), it) }.getOrNull() }
                ?.let { _events.emit(ChatRealtimeEvent.ThreadUpdate(it)) }
            else -> Unit
        }
    }

    private fun buildWsUrl(token: String): String {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val wsBase = when {
            base.startsWith("https://", ignoreCase = true) -> "wss://" + base.substring("https://".length)
            base.startsWith("http://", ignoreCase = true) -> "ws://" + base.substring("http://".length)
            else -> base
        }
        return "$wsBase/ws/chat?token=$token"
    }

    private fun backoff(attempt: Int): Long {
        val capped = attempt.coerceIn(1, 6)
        return (1_000L * (1L shl (capped - 1))).coerceAtMost(30_000L)
    }

    companion object {
        private const val AUTH_CLOSE_CODE = 4001
    }
}
