package me.zhenxin.zmusic.client

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.zhenxin.zmusic.ZMusicPlayer
import me.zhenxin.zmusic.common.ZMusicConstants
import me.zhenxin.zmusic.protocol.PacketCodec
import me.zhenxin.zmusic.protocol.PacketEnvelope
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Collections
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 跨加载器客户端协议状态机，统一处理握手、播放、停止、歌词和状态回报。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
object ZMusicClient {
    private val lock = Any()
    private val seenMessages = LinkedHashSet<String>()
    private var environment: ClientEnvironment? = null
    private var transport: ClientTransport? = null
    private var logger: ClientLogger? = null
    private var playback: PlaybackController? = null
    private var lyricsExecutor: ExecutorService? = null
    private var playerClientId = ""
    private var currentRequestId: String? = null
    private var serverReady = false
    private var lastProgressSentAt = 0L
    private var lyricsState = "none"
    private var lyricsTimeline = LrcTimeline.empty()
    private var translationTimeline = LrcTimeline.empty()

    fun configure(
        environment: ClientEnvironment,
        transport: ClientTransport,
        logger: ClientLogger,
        playback: PlaybackController = NativePlaybackController(environment.gameDirectory),
    ) = synchronized(lock) {
        closeLocked()
        this.environment = environment
        this.transport = transport
        this.logger = logger
        this.playback = playback
        this.lyricsExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "zmusic-lyrics").apply { isDaemon = true }
        }
        this.playerClientId = loadPlayerClientId(environment, logger)
        playback.setListener(object : PlaybackController.Listener {
            override fun onStateChanged(state: Int) = handleStateChanged(state)
            override fun onTrackEnded() = handleTrackEnded()
            override fun onProgress(positionMillis: Long, durationMillis: Long) =
                handleProgress(positionMillis, durationMillis)
            override fun onError(message: String) = handlePlaybackError(message)
        })
    }

    fun onConnected() = synchronized(lock) {
        serverReady = false
        currentRequestId = null
        lyricsState = "none"
        lyricsTimeline = LrcTimeline.empty()
        translationTimeline = LrcTimeline.empty()
        seenMessages.clear()
        val currentEnvironment = environment ?: return@synchronized
        val hello = JsonObject().apply {
            addProperty("protocolVersion", ZMusicConstants.PROTOCOL_VERSION)
            addProperty("modVersion", ZMusicConstants.MOD_VERSION)
            addProperty("minecraftVersion", currentEnvironment.minecraftVersion)
            addProperty("loader", currentEnvironment.loader)
            addProperty("playerClientId", playerClientId)
            add("capabilities", JsonArray().apply {
                listOf("play.url", "stop", "status", "progress", "lyrics.url").forEach(::add)
            })
        }
        send("client.hello", hello)
    }

    fun onPacket(payload: ByteArray) {
        val message = try {
            PacketCodec.decode(payload)
        } catch (exception: RuntimeException) {
            logger?.warn("Rejected invalid ZMusic packet", exception)
            return
        }
        synchronized(lock) {
            if (!rememberMessage(message.id)) return
            when (message.type) {
                "server.hello" -> handleHello(message)
                "server.play" -> handlePlay(message.data)
                "server.stop" -> handleStop(message.data)
                "server.error" -> logger?.warn(
                    "ZMusic server error ${message.data.string("code").orEmpty()}: " +
                        message.data.string("message").orEmpty(),
                )
                else -> sendError(null, "protocol", "unsupported_message", "Unsupported server message: ${message.type}")
            }
        }
    }

    fun onDisconnected() = synchronized(lock) {
        playback?.stop()
        serverReady = false
        currentRequestId = null
        lyricsState = "none"
        lyricsTimeline = LrcTimeline.empty()
        translationTimeline = LrcTimeline.empty()
        seenMessages.clear()
    }

    fun setVolume(volume: Float) = synchronized(lock) {
        playback?.setVolume(volume)
    }

    fun shutdown() = synchronized(lock) {
        closeLocked()
    }

    private fun handleHello(message: PacketEnvelope) {
        val version = message.data.int("protocolVersion")
        if (version != ZMusicConstants.PROTOCOL_VERSION) {
            serverReady = false
            sendError(null, "handshake", "unsupported_protocol", "Server protocol version is not supported")
            return
        }
        serverReady = true
    }

    private fun handlePlay(data: JsonObject) {
        if (!serverReady) {
            sendError(null, "protocol", "not_ready", "Protocol handshake is not complete", true)
            return
        }
        val requestId = data.string("requestId")
        val mode = data.string("mode")
        val song = data.objectValue("song")
        val audio = data.objectValue("audio")
        val lyrics = data.objectValue("lyrics")
        val audioUrl = audio?.string("url")
        val expiresAt = audio?.long("expiresAt") ?: 0
        if (!validUuid(requestId) || mode != "replace" || song == null ||
            !validSong(song) ||
            audio?.string("type") != "url" || !validHttpUrl(audioUrl) ||
            !audio.optionalNumber("expiresAt") || !audio.optionalObject("headers") ||
            !validHeaders(audio.objectValue("headers")) || !data.optionalNullableObject("lyrics") ||
            !validLyrics(lyrics)
        ) {
            sendError(requestId.takeIf(::validUuid), "protocol", "invalid_payload", "Invalid play request")
            return
        }
        if (expiresAt > 0 && expiresAt <= System.currentTimeMillis()) {
            sendError(requestId, "audio", "audio_url_expired", "Audio URL has expired", true)
            return
        }

        if (currentRequestId != null) sendStatus("stopped", "server_replace", playback?.durationMillis() ?: -1)
        currentRequestId = requestId
        lastProgressSentAt = 0
        lyricsState = if (lyrics == null) "none" else "loading"
        lyricsTimeline = LrcTimeline.empty()
        translationTimeline = LrcTimeline.empty()
        sendStatus("loading", "loading_started", -1)
        if (lyrics != null) loadLyrics(requestId!!, lyrics)
        if (playback?.play(audioUrl!!) != true) {
            sendStatus("failed", "player_error", -1)
            sendError(requestId, "playback", "playback_failed", "Playback could not be started")
            clearRequest()
        }
    }

    private fun handleStop(data: JsonObject) {
        if (!serverReady) {
            sendError(null, "protocol", "not_ready", "Protocol handshake is not complete", true)
            return
        }
        val stopRequestId = data.string("requestId")
        val targetRequestId = data.string("targetRequestId")
        if (!validUuid(stopRequestId) || (!targetRequestId.isNullOrBlank() && !validUuid(targetRequestId))) {
            sendError(currentRequestId, "protocol", "invalid_payload", "Invalid stop request")
            return
        }
        if (!targetRequestId.isNullOrBlank() && targetRequestId != currentRequestId) {
            sendError(targetRequestId, "playback", "stale_request", "Stop target is no longer current")
            return
        }
        playback?.stop()
        sendStatus("stopped", "server_stop", playback?.durationMillis() ?: -1)
        clearRequest()
    }

    private fun handleStateChanged(state: Int) = synchronized(lock) {
        if (currentRequestId == null) return@synchronized
        when (state) {
            ZMusicPlayer.STATE_PLAYING -> sendStatus("playing", "play_started", playback?.durationMillis() ?: -1)
            ZMusicPlayer.STATE_ERROR -> {
                sendStatus("failed", "player_error", playback?.durationMillis() ?: -1)
                sendError(currentRequestId, "playback", "playback_failed", "Native player entered error state")
                clearRequest()
            }
        }
    }

    private fun handleTrackEnded() = synchronized(lock) {
        if (currentRequestId == null) return@synchronized
        sendStatus("ended", "natural_end", playback?.durationMillis() ?: -1)
        clearRequest()
    }

    private fun handleProgress(positionMillis: Long, durationMillis: Long) = synchronized(lock) {
        val requestId = currentRequestId ?: return@synchronized
        val now = System.currentTimeMillis()
        if (now - lastProgressSentAt < 1000) return@synchronized
        lastProgressSentAt = now
        val position = positionMillis.coerceAtLeast(0)
        val lyrics = JsonObject().apply {
            addProperty("state", lyricsState)
            if (lyricsState == "ready") {
                val line = lyricsTimeline.lineAt(position)
                addProperty("lineIndex", line.index)
                addProperty("text", line.text)
                val translation = translationTimeline.lineAt(position).text
                if (translation.isNotEmpty()) addProperty("translation", translation)
            }
        }
        val progress = JsonObject().apply {
            addProperty("requestId", requestId)
            addProperty("positionMillis", position)
            addProperty("durationMillis", if (durationMillis > 0) durationMillis else -1)
            add("lyrics", lyrics)
        }
        send("client.progress", progress)
    }

    private fun handlePlaybackError(message: String) = synchronized(lock) {
        if (currentRequestId == null) return@synchronized
        sendStatus("failed", "player_error", playback?.durationMillis() ?: -1)
        sendError(currentRequestId, "playback", "playback_failed", message)
        clearRequest()
    }

    private fun loadLyrics(requestId: String, lyrics: JsonObject) {
        val lyricsUrl = lyrics.string("url") ?: return
        val translationUrl = lyrics.string("translationUrl")
        lyricsExecutor?.execute {
            try {
                val content = downloadText(lyricsUrl)
                val timeline = LrcTimeline.parse(content)
                val translation = if (translationUrl.isNullOrBlank()) {
                    LrcTimeline.empty()
                } else {
                    try {
                        LrcTimeline.parse(downloadText(translationUrl))
                    } catch (exception: Exception) {
                        synchronized(lock) {
                            if (currentRequestId == requestId) {
                                sendError(
                                    requestId,
                                    "lyrics",
                                    "lyrics_translation_load_failed",
                                    "Failed to load translated lyrics",
                                    true,
                                )
                            }
                        }
                        LrcTimeline.empty()
                    }
                }
                synchronized(lock) {
                    if (currentRequestId != requestId) return@synchronized
                    playback?.loadLyrics(content)
                    lyricsTimeline = timeline
                    translationTimeline = translation
                    lyricsState = "ready"
                }
            } catch (exception: Exception) {
                synchronized(lock) {
                    if (currentRequestId != requestId) return@synchronized
                    lyricsState = "failed"
                    sendError(requestId, "lyrics", "lyrics_load_failed", "Failed to load lyrics", true)
                }
            }
        }
    }

    private fun downloadText(value: String): String {
        val connection = URI(value).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 10000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "ZMusic-Mod/${ZMusicConstants.MOD_VERSION}")
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Unexpected lyrics response: $status")
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val length = input.read(buffer)
                    if (length < 0) break
                    total += length
                    if (total > ZMusicConstants.MAX_LYRICS_BYTES) {
                        throw IllegalStateException("Lyrics payload exceeds protocol limit")
                    }
                    output.write(buffer, 0, length)
                }
                return String(output.toByteArray(), StandardCharsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sendStatus(state: String, reason: String, durationMillis: Long) {
        val requestId = currentRequestId ?: return
        send("client.status", JsonObject().apply {
            addProperty("requestId", requestId)
            addProperty("state", state)
            addProperty("reason", reason)
            addProperty("durationMillis", if (durationMillis > 0) durationMillis else -1)
        })
    }

    private fun sendError(
        requestId: String?,
        phase: String,
        code: String,
        message: String,
        retryable: Boolean = false,
    ) {
        send("client.error", JsonObject().apply {
            if (!requestId.isNullOrBlank()) addProperty("requestId", requestId)
            addProperty("phase", phase)
            addProperty("code", code)
            addProperty("message", message)
            addProperty("retryable", retryable)
        })
    }

    private fun send(type: String, data: JsonObject) {
        try {
            if (transport?.send(PacketCodec.encode(type, data)) != true) {
                logger?.warn("ZMusic transport rejected $type")
            }
        } catch (exception: RuntimeException) {
            logger?.warn("Failed to send ZMusic message $type", exception)
        }
    }

    private fun clearRequest() {
        currentRequestId = null
        lyricsState = "none"
        lyricsTimeline = LrcTimeline.empty()
        translationTimeline = LrcTimeline.empty()
    }

    private fun rememberMessage(id: String): Boolean {
        if (!seenMessages.add(id)) return false
        if (seenMessages.size > 256) seenMessages.remove(seenMessages.iterator().next())
        return true
    }

    private fun closeLocked() {
        lyricsExecutor?.shutdownNow()
        lyricsExecutor = null
        playback?.close()
        playback = null
        transport = null
        environment = null
        serverReady = false
        clearRequest()
        seenMessages.clear()
    }

    private fun loadPlayerClientId(environment: ClientEnvironment, logger: ClientLogger): String {
        val file = environment.gameDirectory.resolve("zmusic").resolve("client-id")
        try {
            if (Files.exists(file)) {
                val value = Files.readAllLines(file, StandardCharsets.UTF_8).firstOrNull()?.trim()
                if (validUuid(value)) return value!!
            }
            val value = UUID.randomUUID().toString()
            Files.createDirectories(file.parent)
            Files.write(file, Collections.singletonList(value), StandardCharsets.UTF_8)
            return value
        } catch (exception: Exception) {
            logger.warn("Failed to persist ZMusic player client id", exception)
            return UUID.randomUUID().toString()
        }
    }

    private fun validUuid(value: String?): Boolean = try {
        UUID.fromString(value)
        true
    } catch (exception: RuntimeException) {
        false
    }

    private fun validHttpUrl(value: String?): Boolean {
        if (value == null || value.length > 8192) return false
        return try {
            val uri = URI(value)
            (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank()
        } catch (exception: RuntimeException) {
            false
        }
    }

    private fun validHeaders(headers: JsonObject?): Boolean {
        if (headers == null) return true
        val forbidden = setOf("host", "content-length", "transfer-encoding", "connection", "cookie", "authorization")
        return headers.size() <= 32 && headers.entrySet().all { (name, value) ->
            name.length in 1..128 && name.lowercase(Locale.ROOT) !in forbidden &&
                value.isJsonPrimitive && value.asJsonPrimitive.isString &&
                value.asString.length <= 4096 && '\r' !in value.asString && '\n' !in value.asString
        }
    }

    private fun validSong(song: JsonObject): Boolean {
        val source = song.string("source") ?: return false
        val title = song.string("title") ?: return false
        return source.length in 1..64 && title.length in 1..512
    }

    private fun validLyrics(lyrics: JsonObject?): Boolean {
        if (lyrics == null) return true
        val translationUrl = lyrics.string("translationUrl")
        return lyrics.string("type") == "url" && lyrics.string("format") == "lrc" &&
            validHttpUrl(lyrics.string("url")) && (translationUrl.isNullOrBlank() || validHttpUrl(translationUrl)) &&
            lyrics.optionalSize("sizeBytes") && lyrics.optionalSize("translationSizeBytes")
    }
}

private fun JsonObject.string(name: String): String? {
    val value: JsonElement = get(name) ?: return null
    return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
}

private fun JsonObject.long(name: String): Long? = try {
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
} catch (exception: RuntimeException) {
    null
}

private fun JsonObject.int(name: String): Int? = try {
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
} catch (exception: RuntimeException) {
    null
}

private fun JsonObject.objectValue(name: String): JsonObject? = try {
    get(name)?.takeIf(JsonElement::isJsonObject)?.asJsonObject
} catch (exception: RuntimeException) {
    null
}

private fun JsonObject.optionalObject(name: String): Boolean {
    val value = get(name) ?: return true
    return value.isJsonObject
}

private fun JsonObject.optionalNullableObject(name: String): Boolean {
    val value = get(name) ?: return true
    return value.isJsonNull || value.isJsonObject
}

private fun JsonObject.optionalNumber(name: String): Boolean {
    val value = get(name) ?: return true
    return value.isJsonPrimitive && value.asJsonPrimitive.isNumber
}

private fun JsonObject.optionalSize(name: String): Boolean {
    if (!optionalNumber(name)) return false
    val value = long(name) ?: return !has(name)
    return value in 0..ZMusicConstants.MAX_LYRICS_BYTES.toLong()
}
