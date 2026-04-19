package br.tec.wrcoder.meucondominio.data.remote.ws

import br.tec.wrcoder.meucondominio.core.BuildConfig
import br.tec.wrcoder.meucondominio.core.network.NetworkMonitor
import br.tec.wrcoder.meucondominio.core.storage.TokenStore
import br.tec.wrcoder.meucondominio.data.remote.dto.ChatMessageDto
import br.tec.wrcoder.meucondominio.data.remote.dto.ChatThreadDto
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _events = MutableSharedFlow<ChatRealtimeEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    val events: SharedFlow<ChatRealtimeEvent> = _events.asSharedFlow()

    private var loopJob: Job? = null

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
            val connected = runCatching { connectOnce(token) }
            if (connected.isSuccess) {
                attempt = 0
            } else {
                println("[ChatWS] disconnected: ${connected.exceptionOrNull()?.message}")
            }
            delay(backoff(++attempt))
        }
    }

    private suspend fun connectOnce(token: String) {
        val url = buildWsUrl(token)
        println("[ChatWS] connecting $url")
        val session = http.webSocketSession(url)
        val heartbeat = scope.launch {
            while (isActive) {
                delay(30_000)
                runCatching { session.send(Frame.Text("""{"type":"ping"}""")) }
            }
        }
        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                handleText(frame.readText())
            }
        } finally {
            heartbeat.cancel()
            runCatching { session.close() }
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
}
