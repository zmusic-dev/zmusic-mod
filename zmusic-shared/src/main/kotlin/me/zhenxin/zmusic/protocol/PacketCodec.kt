package me.zhenxin.zmusic.protocol

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.zhenxin.zmusic.common.ZMusicConstants
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Minecraft 插件消息中的统一 ZMPK envelope。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
data class PacketEnvelope(
    val id: String,
    val type: String,
    val timestamp: Long,
    val data: JsonObject,
)

/**
 * 编解码 `ZMPK + version + JSON` 二进制帧。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
object PacketCodec {
    private val magic = byteArrayOf('Z'.code.toByte(), 'M'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte())
    private val gson = Gson()

    fun encode(type: String, data: JsonObject): ByteArray = encode(
        PacketEnvelope(UUID.randomUUID().toString(), type, System.currentTimeMillis(), data),
    )

    fun encode(envelope: PacketEnvelope): ByteArray {
        validateEnvelope(envelope)
        val json = gson.toJson(envelope).toByteArray(StandardCharsets.UTF_8)
        if (json.size > ZMusicConstants.MAX_JSON_BYTES) {
            throw ProtocolException("JSON payload exceeds ${ZMusicConstants.MAX_JSON_BYTES} bytes")
        }
        return magic + byteArrayOf(ZMusicConstants.PROTOCOL_VERSION.toByte()) + json
    }

    fun decode(packet: ByteArray): PacketEnvelope {
        if (packet.size < 5 || packet.size > ZMusicConstants.MAX_PACKET_BYTES) {
            throw ProtocolException("Invalid packet length: ${packet.size}")
        }
        if (!packet.copyOfRange(0, 4).contentEquals(magic)) {
            throw ProtocolException("Invalid packet magic")
        }
        val frameVersion = packet[4].toInt() and 0xff
        if (frameVersion != ZMusicConstants.PROTOCOL_VERSION) {
            throw ProtocolException("Unsupported frame version: $frameVersion")
        }

        val root = try {
            JsonParser().parse(String(packet, 5, packet.size - 5, StandardCharsets.UTF_8)).asJsonObject
        } catch (exception: RuntimeException) {
            throw ProtocolException("Invalid packet JSON", exception)
        }
        val envelope = try {
            PacketEnvelope(
                root.get("id").asString,
                root.get("type").asString,
                root.get("timestamp").asLong,
                root.getAsJsonObject("data"),
            )
        } catch (exception: RuntimeException) {
            throw ProtocolException("Packet envelope is missing required fields", exception)
        }
        validateEnvelope(envelope)
        return envelope
    }

    private fun validateEnvelope(envelope: PacketEnvelope) {
        try {
            UUID.fromString(envelope.id)
        } catch (exception: IllegalArgumentException) {
            throw ProtocolException("Packet id must be a UUID", exception)
        }
        if (envelope.type.isBlank() || envelope.type.length > 128 || envelope.timestamp <= 0) {
            throw ProtocolException("Invalid packet envelope")
        }
    }
}

/**
 * 表示可安全拒绝的通信包校验错误。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
class ProtocolException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
