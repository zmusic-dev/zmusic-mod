package me.zhenxin.zmusic.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.ZMusicPlayer;
import me.zhenxin.zmusic.protocol.ClientTransport;
import me.zhenxin.zmusic.protocol.ProtocolCodec;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 客户端协议状态机，负责握手、服务端命令分发和播放状态回报。
 *
 * @author 真心
 * @since 2023/1/29 22:52
 */
@Log4j2
public final class ClientEvent {
    private static final String PLAYER_CLIENT_ID = UUID.randomUUID().toString();
    private static volatile ClientTransport transport;
    private static volatile String minecraftVersion = "unknown";
    private static volatile String loader = "unknown";
    private static volatile String currentRequestId;
    private static volatile boolean serverReady;
    private static volatile long lastProgressSentAt;
    private static volatile String lyricsState = "none";
    private static final Set<String> SEEN_MESSAGES = new LinkedHashSet<>();

    private ClientEvent() {
    }

    /**
     * 设置当前加载器的上行传输实现和环境信息。
     *
     * @param nextTransport 上行消息出口
     * @param nextMinecraftVersion Minecraft 版本
     * @param nextLoader 加载器标识
     */
    public static void configure(ClientTransport nextTransport, String nextMinecraftVersion, String nextLoader) {
        transport = nextTransport;
        minecraftVersion = nextMinecraftVersion;
        loader = nextLoader;
    }

    /**
     * 在进入服务器后发起业务协议握手。
     */
    public static void onConnected() {
        serverReady = false;
        currentRequestId = null;
        lyricsState = "none";
        synchronized (SEEN_MESSAGES) {
            SEEN_MESSAGES.clear();
        }
        JsonObject data = new JsonObject();
        data.addProperty("protocolVersion", ProtocolCodec.VERSION);
        data.addProperty("modVersion", ZMusic.getVersion());
        data.addProperty("minecraftVersion", minecraftVersion);
        data.addProperty("loader", loader);
        data.addProperty("playerClientId", PLAYER_CLIENT_ID);
        JsonArray capabilities = new JsonArray();
        capabilities.add("play.url");
        capabilities.add("stop");
        capabilities.add("status");
        capabilities.add("progress");
        capabilities.add("lyrics.url");
        data.add("capabilities", capabilities);
        send("client.hello", data);
    }

    /**
     * 解码并处理服务端发来的完整协议帧。
     *
     * @param payload 原始协议帧
     */
    public static void onPacket(byte[] payload) {
        try {
            JsonObject root = ProtocolCodec.decode(payload);
            String messageId = root.get("id").getAsString();
            if (!rememberMessage(messageId)) return;
            String type = root.get("type").getAsString();
            JsonObject data = root.getAsJsonObject("data");
            switch (type) {
                case "server.hello":
                    serverReady = integer(data, "protocolVersion", -1) == ProtocolCodec.VERSION;
                    if (!serverReady) {
                        sendError(null, "handshake", "unsupported_protocol", "Server protocol version is not supported", false);
                    }
                    break;
                case "server.play":
                    onPlay(data);
                    break;
                case "server.stop":
                    onStop(data);
                    break;
                case "server.error":
                    if ("unsupported_protocol".equals(string(data, "code"))) serverReady = false;
                    log.warn("ZMusic server protocol error {}: {}", string(data, "code"), string(data, "message"));
                    break;
                default:
                    sendError(null, "protocol", "unsupported_message", "Unsupported server message: " + type, false);
            }
        } catch (RuntimeException exception) {
            log.warn("Rejected invalid ZMusic server packet: {}", exception.getMessage());
            sendError(null, "protocol", "invalid_payload", "Invalid server packet", false);
        }
    }

    private static void onPlay(JsonObject data) {
        if (!serverReady) {
            sendError(null, "protocol", "not_ready", "Protocol handshake is not complete", true);
            return;
        }
        String requestId = string(data, "requestId");
        JsonObject audio = data.getAsJsonObject("audio");
        JsonObject song = data.getAsJsonObject("song");
        JsonObject lyrics = data.getAsJsonObject("lyrics");
        String audioUrl = audio == null ? "" : string(audio, "url");
        long expiresAt = longValue(audio, "expiresAt", 0);
        if (!isUUID(requestId) || !"replace".equals(string(data, "mode")) ||
                audio == null || !"url".equals(string(audio, "type")) || !isHttpUrl(audioUrl) ||
                song == null || string(song, "source").isEmpty() || string(song, "title").isEmpty() ||
                !validLyrics(lyrics)) {
            sendError(requestId, "protocol", "invalid_payload", "Invalid play request", false);
            return;
        }
        if (expiresAt > 0 && expiresAt <= System.currentTimeMillis()) {
            sendError(requestId, "audio", "audio_url_expired", "Audio URL has expired", true);
            return;
        }
        if (currentRequestId != null) {
            sendStatus("stopped", "server_replace", -1);
        }
        currentRequestId = requestId;
        lyricsState = lyrics == null ? "none" : "loading";
        sendStatus("loading", "loading_started", -1);
        boolean started;
        try {
            started = PacketEvent.onPlay(audioUrl, lyrics, requestId);
        } catch (RuntimeException exception) {
            log.warn("Failed to execute ZMusic play request: {}", exception.getMessage());
            started = false;
        }
        if (!started) {
            sendStatus("failed", "player_error", -1);
            sendError(requestId, "playback", "playback_failed", "Playback could not be started", false);
            currentRequestId = null;
            lyricsState = "none";
        }
    }

    private static void onStop(JsonObject data) {
        if (!serverReady) {
            sendError(null, "protocol", "not_ready", "Protocol handshake is not complete", true);
            return;
        }
        String stopRequestId = string(data, "requestId");
        if (!isUUID(stopRequestId)) {
            sendError(null, "protocol", "invalid_payload", "Invalid stop request", false);
            return;
        }
        String target = string(data, "targetRequestId");
        if (!target.isEmpty() && (!isUUID(target) || !target.equals(currentRequestId))) {
            sendError(target, "playback", "stale_request", "Stop target is no longer current", false);
            return;
        }
        PacketEvent.onStop();
        sendStatus("stopped", "server_stop", duration());
        currentRequestId = null;
        lyricsState = "none";
    }

    /**
     * 接收原生播放器状态变化并映射为协议状态。
     *
     * @param state 原生播放器状态常量
     */
    public static void onPlayerStateChanged(int state) {
        if (currentRequestId == null) return;
        if (state == ZMusicPlayer.STATE_PLAYING) {
            sendStatus("playing", "play_started", duration());
        } else if (state == ZMusicPlayer.STATE_ERROR) {
            sendStatus("failed", "player_error", duration());
            currentRequestId = null;
            lyricsState = "none";
        }
    }

    /**
     * 接收原生播放器自然结束事件。
     */
    public static void onTrackEnded() {
        if (currentRequestId == null) return;
        sendStatus("ended", "natural_end", duration());
        currentRequestId = null;
        lyricsState = "none";
    }

    /**
     * 以不高于 1 Hz 上报播放进度和当前歌词。
     *
     * @param positionMillis 当前进度
     * @param durationMillis 总时长
     */
    public static void onProgress(long positionMillis, long durationMillis) {
        if (currentRequestId == null || System.currentTimeMillis() - lastProgressSentAt < 1000) return;
        lastProgressSentAt = System.currentTimeMillis();
        JsonObject data = new JsonObject();
        data.addProperty("requestId", currentRequestId);
        data.addProperty("positionMillis", Math.max(0, positionMillis));
        data.addProperty("durationMillis", durationMillis > 0 ? durationMillis : -1);
        JsonObject lyrics = new JsonObject();
        String text = ZMusic.getPlayer() == null ? "" : ZMusic.getPlayer().getCurrentLyric();
        lyrics.addProperty("state", lyricsState);
        if ("ready".equals(lyricsState)) {
            lyrics.addProperty("state", "ready");
            lyrics.addProperty("lineIndex", -1);
            lyrics.addProperty("text", text == null ? "" : text);
        }
        data.add("lyrics", lyrics);
        send("client.progress", data);
    }

    /**
     * 接收原生播放器错误事件。
     *
     * @param message 错误摘要
     */
    public static void onPlayerError(String message) {
        if (currentRequestId == null) return;
        sendStatus("failed", "player_error", duration());
        sendError(currentRequestId, "playback", "playback_failed", message, false);
        currentRequestId = null;
        lyricsState = "none";
    }

    /**
     * 断开服务器时停止播放并清理握手和请求状态。
     */
    public static void onDisconnect() {
        PacketEvent.onStop();
        serverReady = false;
        currentRequestId = null;
        lyricsState = "none";
        synchronized (SEEN_MESSAGES) {
            SEEN_MESSAGES.clear();
        }
    }

    static boolean isCurrentRequest(String requestId) {
        return requestId != null && requestId.equals(currentRequestId);
    }

    static void onLyricsLoaded(String requestId) {
        if (isCurrentRequest(requestId)) lyricsState = "ready";
    }

    static void onLyricsError(String requestId, String message) {
        if (!isCurrentRequest(requestId)) return;
        lyricsState = "failed";
        sendError(requestId, "lyrics", "lyrics_load_failed", message, true);
    }

    private static void sendStatus(String state, String reason, long durationMillis) {
        if (currentRequestId == null) return;
        JsonObject data = new JsonObject();
        data.addProperty("requestId", currentRequestId);
        data.addProperty("state", state);
        data.addProperty("reason", reason);
        data.addProperty("durationMillis", durationMillis > 0 ? durationMillis : -1);
        send("client.status", data);
    }

    private static void sendError(String requestId, String phase, String code, String message, boolean retryable) {
        JsonObject data = new JsonObject();
        if (requestId != null && !requestId.isEmpty()) data.addProperty("requestId", requestId);
        data.addProperty("phase", phase);
        data.addProperty("code", code);
        data.addProperty("message", message == null ? "Unknown error" : message);
        data.addProperty("retryable", retryable);
        send("client.error", data);
    }

    private static void send(String type, JsonObject data) {
        ClientTransport active = transport;
        if (active != null) active.send(ProtocolCodec.encode(type, data));
    }

    private static long duration() {
        return ZMusic.getPlayer() == null ? -1 : ZMusic.getPlayer().getDuration();
    }

    private static boolean isUUID(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean validLyrics(JsonObject lyrics) {
        if (lyrics == null) return true;
        if (!"url".equals(string(lyrics, "type")) || !"lrc".equalsIgnoreCase(string(lyrics, "format")) ||
                !isHttpUrl(string(lyrics, "url"))) {
            return false;
        }
        String translationUrl = string(lyrics, "translationUrl");
        return translationUrl.isEmpty() || isHttpUrl(translationUrl);
    }

    private static boolean rememberMessage(String id) {
        synchronized (SEEN_MESSAGES) {
            if (!SEEN_MESSAGES.add(id)) return false;
            if (SEEN_MESSAGES.size() > 256) {
                SEEN_MESSAGES.remove(SEEN_MESSAGES.iterator().next());
            }
            return true;
        }
    }

    private static int integer(JsonObject object, String name, int fallback) {
        try {
            return object == null || !object.has(name) ? fallback : object.get(name).getAsInt();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String name, long fallback) {
        try {
            return object == null || !object.has(name) ? fallback : object.get(name).getAsLong();
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object == null ? null : object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
