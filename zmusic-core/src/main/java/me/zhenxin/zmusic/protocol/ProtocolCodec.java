package me.zhenxin.zmusic.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * 编解码 ZMusic 的 {@code ZMPK + version + JSON} Minecraft 通信帧。
 *
 * @author 真心
 * @since 3.8.0
 */
public final class ProtocolCodec {
    public static final String CHANNEL = "zmusic:packet";
    public static final int VERSION = 1;
    public static final int MAX_PACKET_BYTES = 32760;
    private static final byte[] MAGIC = new byte[]{'Z', 'M', 'P', 'K'};
    private static final Gson GSON = new Gson();

    private ProtocolCodec() {
    }

    /**
     * 创建并编码一条客户端消息。
     *
     * @param type 消息类型
     * @param data 业务数据
     * @return 完整二进制帧
     */
    public static byte[] encode(String type, JsonObject data) {
        JsonObject root = new JsonObject();
        root.addProperty("id", UUID.randomUUID().toString());
        root.addProperty("type", type);
        root.addProperty("timestamp", System.currentTimeMillis());
        root.add("data", data);
        byte[] json = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        if (json.length > MAX_PACKET_BYTES - 5) {
            throw new ProtocolException("JSON payload exceeds protocol limit");
        }
        byte[] packet = new byte[json.length + 5];
        System.arraycopy(MAGIC, 0, packet, 0, MAGIC.length);
        packet[4] = VERSION;
        System.arraycopy(json, 0, packet, 5, json.length);
        return packet;
    }

    /**
     * 解码并校验服务端消息。
     *
     * @param packet 完整二进制帧
     * @return JSON envelope
     */
    public static JsonObject decode(byte[] packet) {
        if (packet == null || packet.length < 5 || packet.length > MAX_PACKET_BYTES) {
            throw new ProtocolException("Invalid packet length");
        }
        if (!Arrays.equals(MAGIC, Arrays.copyOfRange(packet, 0, 4))) {
            throw new ProtocolException("Invalid packet magic");
        }
        if ((packet[4] & 0xff) != VERSION) {
            throw new ProtocolException("Unsupported frame version");
        }
        final JsonObject root;
        try {
            root = JsonParser.parseString(new String(packet, 5, packet.length - 5, StandardCharsets.UTF_8)).getAsJsonObject();
            UUID.fromString(root.get("id").getAsString());
            root.get("type").getAsString();
            root.get("timestamp").getAsLong();
            root.getAsJsonObject("data");
        } catch (RuntimeException exception) {
            throw new ProtocolException("Invalid packet envelope", exception);
        }
        return root;
    }
}
