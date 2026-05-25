package me.zhenxin.zmusic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * ZMusic Player JNI 桥接
 *
 * <p>通过 JNI 桥接 Zig 层的播放引擎，提供完整的音乐播放器 Java 接口。
 * 包含播放控制、队列管理、歌词加载、事件回调等能力。</p>
 *
 * @author 真心
 * @since 2026-04-24 00:00
 */
public class ZMusicPlayer {

    private static final String NATIVE_RESOURCE_ROOT = "META-INF/native";

    static {
        loadNativeLibrary();
    }

    private long handle;

    // ---- Native 方法声明 ----

    // --- 生命周期管理 ---
    private native long nativeInit();
    private native void nativeDestroy(long handle);

    // --- 播放控制 ---
    private native int nativePlay(long handle, String url);
    private native int nativePause(long handle);
    private native int nativeStop(long handle);
    private native int nativeResume(long handle);
    private native int nativeSeek(long handle, long positionMs);

    // --- 状态查询 ---
    private native int nativeGetState(long handle);
    private native long nativeGetPosition(long handle);
    private native long nativeGetDuration(long handle);
    private native float nativeGetVolume(long handle);
    private native int nativeSetVolume(long handle, float volume);

    // --- 队列操作 ---
    private native void nativeEnqueue(long handle, String url, String title, String artist);
    private native void nativeEnqueueNext(long handle, String url, String title, String artist);
    private native void nativeRemoveFromQueue(long handle, int index);
    private native void nativeClearQueue(long handle);
    private native void nativePlayNext(long handle);
    private native void nativePlayPrevious(long handle);
    private native void nativePlayAtIndex(long handle, int index);
    private native int nativeGetQueueSize(long handle);
    private native int nativeGetCurrentIndex(long handle);

    // --- 歌词 ---
    private native void nativeLoadLyrics(long handle, String lrcContent);
    private native String nativeGetCurrentLyric(long handle);
    private native String nativeGetLyricLineAt(long handle, long timeMs);

    // --- 模式控制 ---
    private native void nativeSetRepeatMode(long handle, int mode);
    private native void nativeSetShuffle(long handle, boolean enabled);

    // --- 事件轮询 ---
    private native int nativePollEvent(long handle);

    // ---- 事件常量 ----
    public static final int EVENT_NONE = 0;
    public static final int EVENT_STATE_CHANGED = 1;
    public static final int EVENT_TRACK_ENDED = 2;
    public static final int EVENT_PROGRESS_UPDATE = 3;
    public static final int EVENT_ERROR = 4;
    public static final int EVENT_BUFFERING = 5;

    // ---- 播放状态常量 ----
    public static final int STATE_STOPPED = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_PLAYING = 2;
    public static final int STATE_PAUSED = 3;
    public static final int STATE_ERROR = 4;

    // ---- 循环模式常量 ----
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;

    private volatile boolean running = true;

    /**
     * 构造函数：初始化底层播放器。
     *
     * @throws RuntimeException 原生层初始化失败时抛出
     */
    public ZMusicPlayer() {
        handle = nativeInit();
        if (handle == 0) {
            throw new RuntimeException("ZMusicPlayer 初始化失败");
        }
    }

    /**
     * 销毁播放器，释放原生资源。
     */
    public void destroy() {
        running = false;
        if (handle != 0) {
            nativeDestroy(handle);
            handle = 0;
        }
    }

    /**
     * 播放指定 URL 的音频。
     *
     * @param url 音频资源的 URL
     * @return 0 表示成功，非零表示错误码
     */
    public int play(String url) { return nativePlay(handle, url); }

    /**
     * 暂停当前播放。
     *
     * @return 0 表示成功，非零表示错误码
     */
    public int pause() { return nativePause(handle); }

    /**
     * 停止播放并释放音频资源。
     *
     * @return 0 表示成功，非零表示错误码
     */
    public int stop() { return nativeStop(handle); }

    /**
     * 恢复已暂停的播放。
     *
     * @return 0 表示成功，非零表示错误码
     */
    public int resume() { return nativeResume(handle); }

    /**
     * 跳转到指定播放位置。
     *
     * @param positionMs 目标位置（毫秒）
     * @return 0 表示成功，非零表示错误码
     */
    public int seek(long positionMs) { return nativeSeek(handle, positionMs); }

    /**
     * 获取当前播放状态。
     *
     * @return 状态码
     */
    public int getState() { return nativeGetState(handle); }

    /**
     * 获取当前播放位置（毫秒）。
     */
    public long getPosition() { return nativeGetPosition(handle); }

    /**
     * 获取当前曲目的总时长（毫秒）。
     */
    public long getDuration() { return nativeGetDuration(handle); }

    /**
     * 获取当前音量（0.0 ~ 1.0）。
     */
    public float getVolume() { return nativeGetVolume(handle); }

    /**
     * 设置音量（0.0 ~ 1.0）。
     *
     * @return 0 表示成功，非零表示错误码
     */
    public int setVolume(float volume) { return nativeSetVolume(handle, volume); }

    /**
     * 将曲目追加到播放队列末尾。
     */
    public void enqueue(String url, String title, String artist) {
        nativeEnqueue(handle, url, title, artist);
    }

    /**
     * 将曲目插入到当前播放曲目之后。
     */
    public void enqueueNext(String url, String title, String artist) {
        nativeEnqueueNext(handle, url, title, artist);
    }

    /**
     * 移除播放队列中指定索引的曲目。
     */
    public void removeFromQueue(int index) { nativeRemoveFromQueue(handle, index); }

    /**
     * 清空播放队列中的所有曲目。
     */
    public void clearQueue() { nativeClearQueue(handle); }

    /**
     * 跳到下一首曲目。
     */
    public void playNext() { nativePlayNext(handle); }

    /**
     * 跳到上一首曲目。
     */
    public void playPrevious() { nativePlayPrevious(handle); }

    /**
     * 跳到播放队列中指定索引的曲目并开始播放。
     */
    public void playAtIndex(int index) { nativePlayAtIndex(handle, index); }

    /**
     * 获取播放队列中的曲目数量。
     */
    public int getQueueSize() { return nativeGetQueueSize(handle); }

    /**
     * 获取当前播放曲目在队列中的索引。
     */
    public int getCurrentIndex() { return nativeGetCurrentIndex(handle); }

    /**
     * 加载 LRC 格式的歌词内容。
     */
    public void loadLyrics(String lrcContent) { nativeLoadLyrics(handle, lrcContent); }

    /**
     * 获取当前播放时间对应的歌词行文本。
     */
    public String getCurrentLyric() { return nativeGetCurrentLyric(handle); }

    /**
     * 设置循环模式。
     *
     * @param mode 0=不循环, 1=单曲循环, 2=列表循环
     */
    public void setRepeatMode(int mode) { nativeSetRepeatMode(handle, mode); }

    /**
     * 启用或禁用随机播放。
     */
    public void setShuffle(boolean enabled) { nativeSetShuffle(handle, enabled); }

    // ---- 事件轮询 ----

    /**
     * 事件监听器接口。
     */
    public interface EventListener {
        void onStateChanged(int state);
        void onTrackEnded();
        void onProgress(long positionMs, long durationMs);
        void onError(String message);
        void onBuffering(boolean buffering);
    }

    private EventListener listener;

    /**
     * 设置事件监听器。
     */
    public void setEventListener(EventListener listener) {
        this.listener = listener;
        if (listener != null) {
            startPollingThread();
        }
    }

    private void startPollingThread() {
        Thread thread = new Thread(() -> {
            while (running && handle != 0) {
                int event = nativePollEvent(handle);
                if (event != EVENT_NONE && listener != null) {
                    if (event == EVENT_STATE_CHANGED) {
                        listener.onStateChanged(nativeGetState(handle));
                    } else if (event == EVENT_TRACK_ENDED) {
                        listener.onTrackEnded();
                    } else if (event == EVENT_PROGRESS_UPDATE) {
                        listener.onProgress(nativeGetPosition(handle), nativeGetDuration(handle));
                    } else if (event == EVENT_ERROR) {
                        listener.onError("播放错误");
                    } else if (event == EVENT_BUFFERING) {
                        listener.onBuffering(true);
                    }
                }
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
            }
        });
        thread.setDaemon(true);
        thread.setName("zmusic-event-poll");
        thread.start();
    }

    private static void loadNativeLibrary() {
        try {
            System.loadLibrary("zmusic");
            return;
        } catch (UnsatisfiedLinkError ignored) {
        }

        String libName = getNativeLibName();
        String platform = getNativePlatform();
        String resourcePath = NATIVE_RESOURCE_ROOT + "/" + platform + "/" + libName;
        try (InputStream is = ZMusicPlayer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Native library not found in classpath: " + resourcePath);
            }
            Path libDir = Paths.get(System.getProperty("user.home"), ".zmusic", "native", platform);
            Files.createDirectories(libDir);
            Path libFile = libDir.resolve(libName);
            Files.copy(is, libFile, StandardCopyOption.REPLACE_EXISTING);
            System.load(libFile.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract native library", e);
        }
    }

    private static String getNativeLibName() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) return "libzmusic.so";
        if (os.contains("win")) return "zmusic.dll";
        if (os.contains("mac")) return "libzmusic.dylib";
        return "libzmusic.so";
    }

    private static String getNativePlatform() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = normalizeArch(System.getProperty("os.arch").toLowerCase(Locale.ROOT));

        if (os.contains("linux")) return arch + "-linux";
        if (os.contains("win")) return arch + "-windows";
        if (os.contains("mac")) return arch + "-macos";
        return arch + "-linux";
    }

    private static String normalizeArch(String arch) {
        if ("amd64".equals(arch) || "x86_64".equals(arch)) {
            return "x86_64";
        }
        if ("aarch64".equals(arch) || "arm64".equals(arch)) {
            return "aarch64";
        }
        return arch;
    }
}
