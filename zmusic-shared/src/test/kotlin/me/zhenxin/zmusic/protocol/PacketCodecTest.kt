package me.zhenxin.zmusic.protocol

import com.google.gson.JsonObject
import me.zhenxin.zmusic.common.ZMusicConstants
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PacketCodecTest {
    @Test
    fun `round trips a valid envelope`() {
        val envelope = PacketEnvelope(
            UUID.randomUUID().toString(),
            "client.hello",
            1234,
            JsonObject().apply { addProperty("protocolVersion", 1) },
        )

        assertEquals(envelope, PacketCodec.decode(PacketCodec.encode(envelope)))
    }

    @Test
    fun `rejects invalid magic and frame version`() {
        val invalidMagic = PacketCodec.encode("client.hello", JsonObject()).apply { this[0] = 'X'.code.toByte() }
        val invalidVersion = PacketCodec.encode("client.hello", JsonObject()).apply { this[4] = 2 }

        assertFailsWith<ProtocolException> { PacketCodec.decode(invalidMagic) }
        assertFailsWith<ProtocolException> { PacketCodec.decode(invalidVersion) }
    }

    @Test
    fun `rejects missing fields and oversized payloads`() {
        val json = """{"id":"${UUID.randomUUID()}","type":"client.hello","timestamp":1234}"""
            .toByteArray(StandardCharsets.UTF_8)
        val missingData = byteArrayOf('Z'.code.toByte(), 'M'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), 1) + json
        val oversized = JsonObject().apply { addProperty("value", "x".repeat(ZMusicConstants.MAX_JSON_BYTES)) }

        assertFailsWith<ProtocolException> { PacketCodec.decode(missingData) }
        assertFailsWith<ProtocolException> { PacketCodec.encode("client.error", oversized) }
    }
}
