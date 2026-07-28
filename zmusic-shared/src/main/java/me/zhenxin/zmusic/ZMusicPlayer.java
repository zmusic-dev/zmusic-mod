package me.zhenxin.zmusic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

/**
 * 与 zmusic-player 原生音频引擎绑定的 Java 8 JNI 入口。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
public final class ZMusicPlayer implements AutoCloseable {
    /** 播放器已停止。 */
    public static final int STATE_STOPPED = 0;
    /** 播放器正在加载。 */
    public static final int STATE_LOADING = 1;
    /** 播放器正在播放。 */
    public static final int STATE_PLAYING = 2;
    /** 播放器已暂停。 */
    public static final int STATE_PAUSED = 3;
    /** 播放器发生错误。 */
    public static final int STATE_ERROR = 4;

    private static final int EVENT_NONE = 0;
    private static final int EVENT_STATE_CHANGED = 1;
    private static final int EVENT_TRACK_ENDED = 2;
    private static final int EVENT_PROGRESS_UPDATE = 3;
    private static final int EVENT_ERROR = 4;
    private static final String NATIVE_RESOURCE_ROOT = "META-INF/native";
    private static final Object NATIVE_LOAD_LOCK = new Object();
    private static volatile boolean nativeLibraryLoaded;
    private static volatile Path nativeDirectory;

    private final Object nativeCallLock = new Object();
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "zmusic-command");
            thread.setDaemon(true);
            return thread;
        }
    });
    private volatile boolean running = true;
    private volatile EventListener listener;
    private volatile Thread pollingThread;
    private volatile float currentVolume = -1.0f;
    private long handle;

    private native long nativeInit();
    private native void nativeDestroy(long handle);
    private native int nativePlay(long handle, String url);
    private native int nativeStop(long handle);
    private native int nativeGetState(long handle);
    private native long nativeGetPosition(long handle);
    private native long nativeGetDuration(long handle);
    private native int nativeSetVolume(long handle, float volume);
    private native void nativeLoadLyrics(long handle, String lrcContent);
    private native String nativeGetCurrentLyric(long handle);
    private native int nativePollEvent(long handle);

    /**
     * 原生播放器事件出口。
     *
     * @author 真心
     * @since 2026-07-28 00:00
     */
    public interface EventListener {
        /**
         * 播放状态变化。
         *
         * @param state 新状态
         */
        void onStateChanged(int state);

        /** 当前曲目自然结束。 */
        void onTrackEnded();

        /**
         * 播放进度更新。
         *
         * @param positionMillis 当前位置（毫秒）
         * @param durationMillis 总时长（毫秒）
         */
        void onProgress(long positionMillis, long durationMillis);

        /**
         * 播放器发生错误。
         *
         * @param message 错误信息
         */
        void onError(String message);
    }

    /** 创建并初始化原生播放器。 */
    public ZMusicPlayer() {
        ensureNativeLibraryLoaded();
        handle = nativeInit();
        if (handle == 0) {
            throw new IllegalStateException("ZMusic native player initialization failed");
        }
    }

    /**
     * 设置原生库提取目录。
     *
     * @param directory 提取目录
     */
    public static void setNativeDirectory(Path directory) {
        nativeDirectory = directory;
    }

    /**
     * 设置播放器事件监听器。
     *
     * @param nextListener 监听器
     */
    public void setEventListener(EventListener nextListener) {
        listener = nextListener;
        if (nextListener != null) {
            startPollingThread();
        }
    }

    /**
     * 异步播放 URL。
     *
     * @param url HTTP 或 HTTPS 音频地址
     * @return 命令是否已进入执行队列
     */
    public boolean playAsync(final String url) {
        return execute(new Runnable() {
            @Override
            public void run() {
                synchronized (nativeCallLock) {
                    if (!running || handle == 0) return;
                    nativeStop(handle);
                    if (nativePlay(handle, url) != 0) {
                        EventListener current = listener;
                        if (current != null) current.onError("Native player rejected audio URL");
                    }
                }
            }
        });
    }

    /** 异步停止当前播放。 */
    public void stopAsync() {
        execute(new Runnable() {
            @Override
            public void run() {
                synchronized (nativeCallLock) {
                    if (running && handle != 0) nativeStop(handle);
                }
            }
        });
    }

    /**
     * 获取当前播放位置。
     *
     * @return 毫秒位置
     */
    public long getPosition() {
        synchronized (nativeCallLock) {
            return handle == 0 ? 0 : nativeGetPosition(handle);
        }
    }

    /**
     * 获取当前曲目时长。
     *
     * @return 毫秒时长，未知时为负数
     */
    public long getDuration() {
        synchronized (nativeCallLock) {
            return handle == 0 ? -1 : nativeGetDuration(handle);
        }
    }

    /**
     * 获取播放器状态。
     *
     * @return {@code STATE_*} 常量之一
     */
    public int getState() {
        synchronized (nativeCallLock) {
            return handle == 0 ? STATE_STOPPED : nativeGetState(handle);
        }
    }

    /**
     * 异步加载 LRC 歌词。
     *
     * @param content LRC 文本
     */
    public void loadLyrics(final String content) {
        execute(new Runnable() {
            @Override
            public void run() {
                synchronized (nativeCallLock) {
                    if (running && handle != 0) nativeLoadLyrics(handle, content);
                }
            }
        });
    }

    /**
     * 获取当前歌词行。
     *
     * @return 当前歌词，无歌词时为空字符串
     */
    public String getCurrentLyric() {
        synchronized (nativeCallLock) {
            String line = handle == 0 ? null : nativeGetCurrentLyric(handle);
            return line == null ? "" : line;
        }
    }

    /**
     * 异步设置播放音量。
     *
     * @param volume 音量，自动限制到 0 到 1
     */
    public void setVolume(final float volume) {
        final float clamped = Math.max(0.0f, Math.min(1.0f, volume));
        if (Float.compare(currentVolume, clamped) == 0) return;
        currentVolume = clamped;
        execute(new Runnable() {
            @Override
            public void run() {
                synchronized (nativeCallLock) {
                    if (running && handle != 0) nativeSetVolume(handle, clamped);
                }
            }
        });
    }

    /** 释放播放器与原生资源。 */
    @Override
    public synchronized void close() {
        if (!running) return;
        running = false;
        commandExecutor.shutdownNow();
        Thread thread = pollingThread;
        if (thread != null) thread.interrupt();
        synchronized (nativeCallLock) {
            if (handle != 0) {
                nativeDestroy(handle);
                handle = 0;
            }
        }
    }

    private boolean execute(Runnable command) {
        try {
            commandExecutor.execute(command);
            return true;
        } catch (RejectedExecutionException ignored) {
            return false;
        }
    }

    private synchronized void startPollingThread() {
        if (pollingThread != null && pollingThread.isAlive()) return;
        pollingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running && handle != 0) {
                    int event;
                    synchronized (nativeCallLock) {
                        event = nativePollEvent(handle);
                    }
                    EventListener current = listener;
                    if (current != null) {
                        if (event == EVENT_STATE_CHANGED) {
                            current.onStateChanged(getState());
                        } else if (event == EVENT_TRACK_ENDED) {
                            current.onTrackEnded();
                        } else if (event == EVENT_PROGRESS_UPDATE) {
                            current.onProgress(getPosition(), getDuration());
                        } else if (event == EVENT_ERROR) {
                            current.onError("Native player reported playback error");
                        }
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "zmusic-event-poll");
        pollingThread.setDaemon(true);
        pollingThread.start();
    }

    private static void ensureNativeLibraryLoaded() {
        if (nativeLibraryLoaded) return;
        synchronized (NATIVE_LOAD_LOCK) {
            if (nativeLibraryLoaded) return;
            loadNativeLibrary();
            nativeLibraryLoaded = true;
        }
    }

    private static void loadNativeLibrary() {
        try {
            System.loadLibrary("zmusic");
            return;
        } catch (UnsatisfiedLinkError ignored) {
        }

        String libraryName = nativeLibraryName();
        String resourcePath = NATIVE_RESOURCE_ROOT + "/" + nativePlatform() + "/" + libraryName;
        try (InputStream input = ZMusicPlayer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) throw new IOException("Missing native resource: " + resourcePath);
            byte[] bytes = readAllBytes(input);
            String hash = sha256(bytes).substring(0, 16);
            Path directory = nativeDirectory;
            if (directory == null) throw new IOException("Native extraction directory is not configured");
            Files.createDirectories(directory);
            Path target = directory.resolve(hashedName(libraryName, hash));
            writeIfAbsent(directory, target, bytes, hash);
            System.load(target.toAbsolutePath().toString());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to load bundled ZMusic native library", exception);
        }
    }

    private static void writeIfAbsent(Path directory, Path target, byte[] bytes, String hash)
            throws IOException, NoSuchAlgorithmException {
        if (validFile(target, hash)) return;
        Files.deleteIfExists(target);
        Path temporary = Files.createTempFile(directory, "zmusic-", ".tmp");
        try {
            Files.write(temporary, bytes, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException ignored) {
            } catch (IOException exception) {
                try {
                    Files.move(temporary, target);
                } catch (FileAlreadyExistsException ignored) {
                }
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        if (!validFile(target, hash)) throw new IOException("Invalid cached native library: " + target);
    }

    private static boolean validFile(Path file, String hash) throws IOException, NoSuchAlgorithmException {
        return Files.exists(file) && sha256(Files.readAllBytes(file)).startsWith(hash);
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) >= 0) output.write(buffer, 0, length);
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder(digest.length * 2);
        for (byte item : digest) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }

    private static String hashedName(String name, String hash) {
        int extension = name.lastIndexOf('.');
        return extension < 1 ? name + "-" + hash
                : name.substring(0, extension) + "-" + hash + name.substring(extension);
    }

    private static String nativeLibraryName() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "zmusic.dll";
        if (os.contains("mac")) return "libzmusic.dylib";
        return "libzmusic.so";
    }

    private static String nativePlatform() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if ("amd64".equals(arch)) arch = "x86_64";
        if ("arm64".equals(arch)) arch = "aarch64";
        if (os.contains("linux") && "x86_64".equals(arch)) return "x86_64-linux";
        if (os.contains("win") && "x86_64".equals(arch)) return "x86_64-windows";
        if (os.contains("mac") && ("x86_64".equals(arch) || "aarch64".equals(arch))) return arch + "-macos";
        throw new UnsupportedOperationException("Unsupported native platform: " + os + " " + arch);
    }
}
