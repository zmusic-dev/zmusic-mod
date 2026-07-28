package me.zhenxin.zmusic.client

import com.google.gson.JsonObject
import me.zhenxin.zmusic.ZMusicPlayer
import me.zhenxin.zmusic.common.ZMusicConstants
import me.zhenxin.zmusic.protocol.PacketCodec
import me.zhenxin.zmusic.protocol.PacketEnvelope
import java.nio.file.Files
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZMusicClientTest {
    @AfterTest
    fun shutdown() = ZMusicClient.shutdown()

    @Test
    fun `sends hello with a persistent client id`() {
        val directory = Files.createTempDirectory("zmusic-client-test")
        val first = Fixture(directory.toString())
        first.connect()
        val firstId = first.messages().single().data.get("playerClientId").asString
        ZMusicClient.shutdown()

        val second = Fixture(directory.toString())
        second.connect()
        val secondId = second.messages().single().data.get("playerClientId").asString

        assertEquals(firstId, secondId)
        assertEquals(ZMusicConstants.PROTOCOL_VERSION, second.messages().single().data.get("protocolVersion").asInt)
    }

    @Test
    fun `maps server play and native events to client status and progress`() {
        val fixture = Fixture()
        fixture.connect()
        fixture.receive("server.hello", JsonObject().apply { addProperty("protocolVersion", 1) })
        fixture.clearMessages()
        val requestId = UUID.randomUUID().toString()

        fixture.receive("server.play", playData(requestId))
        fixture.playback.emitState(ZMusicPlayer.STATE_PLAYING)
        fixture.playback.emitProgress(1500, 3000)

        assertEquals("https://example.com/audio.mp3", fixture.playback.playedUrl)
        assertEquals(
            listOf("client.status", "client.status", "client.progress"),
            fixture.messages().map { it.type },
        )
        assertEquals("loading", fixture.messages()[0].data.get("state").asString)
        assertEquals("playing", fixture.messages()[1].data.get("state").asString)
        assertEquals(requestId, fixture.messages()[2].data.get("requestId").asString)
    }

    @Test
    fun `stops only the current request`() {
        val fixture = Fixture()
        fixture.connect()
        fixture.receive("server.hello", JsonObject().apply { addProperty("protocolVersion", 1) })
        val requestId = UUID.randomUUID().toString()
        fixture.receive("server.play", playData(requestId))
        fixture.clearMessages()

        fixture.receive("server.stop", JsonObject().apply {
            addProperty("requestId", UUID.randomUUID().toString())
            addProperty("targetRequestId", requestId)
            addProperty("reason", "command")
        })

        assertTrue(fixture.playback.stopped)
        assertEquals("stopped", fixture.messages().single().data.get("state").asString)
    }

    @Test
    fun `rejects malformed optional play fields`() {
        val fixture = Fixture()
        fixture.connect()
        fixture.receive("server.hello", JsonObject().apply { addProperty("protocolVersion", 1) })
        fixture.clearMessages()
        val request = playData(UUID.randomUUID().toString()).apply {
            getAsJsonObject("audio").addProperty("headers", "not-an-object")
        }

        fixture.receive("server.play", request)

        assertFalse(fixture.playback.played)
        assertEquals("invalid_payload", fixture.messages().single().data.get("code").asString)
    }

    @Test
    fun `deduplicates server messages by id`() {
        val fixture = Fixture()
        fixture.connect()
        fixture.receive("server.hello", JsonObject().apply { addProperty("protocolVersion", 1) })
        fixture.clearMessages()
        val message = PacketEnvelope(
            UUID.randomUUID().toString(),
            "server.play",
            System.currentTimeMillis(),
            playData(UUID.randomUUID().toString()),
        )

        fixture.receive(message)
        fixture.receive(message)

        assertEquals(1, fixture.playback.playCount)
    }

    private class Fixture(directory: String = Files.createTempDirectory("zmusic-client-test").toString()) {
        private val sent = mutableListOf<ByteArray>()
        val playback = FakePlayback()

        init {
            ZMusicClient.configure(
                ClientEnvironment("1.21.8", "fabric", java.nio.file.Paths.get(directory)),
                ClientTransport { sent += it; true },
                object : ClientLogger {
                    override fun info(message: String) = Unit
                    override fun warn(message: String, throwable: Throwable?) = Unit
                },
                playback,
            )
        }

        fun connect() = ZMusicClient.onConnected()
        fun clearMessages() = sent.clear()
        fun messages() = sent.map(PacketCodec::decode)
        fun receive(type: String, data: JsonObject) = ZMusicClient.onPacket(
            PacketCodec.encode(PacketEnvelope(UUID.randomUUID().toString(), type, 1, data)),
        )
        fun receive(message: PacketEnvelope) = ZMusicClient.onPacket(PacketCodec.encode(message))
    }

    private class FakePlayback : PlaybackController {
        private lateinit var listener: PlaybackController.Listener
        var playedUrl = ""
        var playCount = 0
        var stopped = false
        val played: Boolean get() = playCount > 0

        override fun setListener(listener: PlaybackController.Listener) {
            this.listener = listener
        }

        override fun play(url: String): Boolean {
            playedUrl = url
            playCount++
            return true
        }

        override fun stop() {
            stopped = true
        }

        override fun loadLyrics(content: String) = Unit
        override fun currentLyric() = ""
        override fun positionMillis() = 0L
        override fun durationMillis() = 3000L
        override fun setVolume(volume: Float) = Unit
        override fun close() = Unit
        fun emitState(state: Int) = listener.onStateChanged(state)
        fun emitProgress(position: Long, duration: Long) = listener.onProgress(position, duration)
    }
}

private fun playData(requestId: String) = JsonObject().apply {
    addProperty("requestId", requestId)
    addProperty("mode", "replace")
    add("song", JsonObject().apply {
        addProperty("source", "custom")
        addProperty("title", "Test")
    })
    add("audio", JsonObject().apply {
        addProperty("type", "url")
        addProperty("url", "https://example.com/audio.mp3")
    })
}
