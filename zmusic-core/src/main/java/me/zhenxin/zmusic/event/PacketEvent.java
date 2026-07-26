package me.zhenxin.zmusic.event;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 发包事件
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/29 22:50
 */
@Log4j2
class PacketEvent {
    private static final int MAX_LYRICS_BYTES = 262144;
    private static final ExecutorService LYRICS_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "zmusic-lyrics");
        thread.setDaemon(true);
        return thread;
    });

    public static boolean onPlay(String audioUrl, JsonObject lyrics, String requestId) {
        log.info("Play music from {}", safeUrl(audioUrl));
        if (audioUrl == null || audioUrl.trim().isEmpty()) {
            log.warn("Ignored empty ZMusic play url");
            return false;
        }
        if (ZMusic.getSoundManager() == null) {
            log.warn("ZMusic SoundManager is not initialized");
        } else {
            log.info("Stopping vanilla music before ZMusic playback");
            ZMusic.getSoundManager().stop();
        }
        if (ZMusic.getPlayer() == null) {
            log.warn("ZMusic player is not initialized");
            return false;
        }
        try {
            ZMusic.getPlayer().playAsync(audioUrl);
        } catch (RuntimeException exception) {
            log.warn("Failed to start ZMusic playback: {}", exception.getMessage());
            return false;
        }
        String lyricsUrl = string(lyrics, "url");
        if (!lyricsUrl.isEmpty()) {
            LYRICS_EXECUTOR.execute(() -> loadLyrics(lyricsUrl, requestId));
        }
        return true;
    }

    public static void onStop() {
        log.info("Stop ZMusic playback");
        if (ZMusic.getPlayer() == null) {
            log.warn("ZMusic player is not initialized");
            return;
        }
        ZMusic.getPlayer().stopAsync();
    }

    private static void loadLyrics(String lyricsUrl, String requestId) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(lyricsUrl).toURL().openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            int contentLength = connection.getContentLength();
            if (contentLength > MAX_LYRICS_BYTES) {
                throw new IllegalArgumentException("Lyrics exceed protocol limit");
            }
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_LYRICS_BYTES) throw new IllegalArgumentException("Lyrics exceed protocol limit");
                    output.write(buffer, 0, read);
                }
                if (ClientEvent.isCurrentRequest(requestId) && ZMusic.getPlayer() != null) {
                    ZMusic.getPlayer().loadLyrics(new String(output.toByteArray(), StandardCharsets.UTF_8));
                    ClientEvent.onLyricsLoaded(requestId);
                }
            }
        } catch (Exception exception) {
            log.warn("Failed to load ZMusic lyrics from {}: {}", safeUrl(lyricsUrl), exception.getMessage());
            ClientEvent.onLyricsError(requestId, "Failed to load lyrics");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object == null ? null : object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String safeUrl(String value) {
        try {
            URI uri = URI.create(value);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (Exception exception) {
            return "<invalid-url>";
        }
    }
}
