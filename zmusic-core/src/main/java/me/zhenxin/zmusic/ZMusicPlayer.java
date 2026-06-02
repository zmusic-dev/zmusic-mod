package me.zhenxin.zmusic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static final Object NATIVE_LOAD_LOCK = new Object();
    private static volatile boolean nativeLibraryLoaded;
    private static volatile Path nativeDirectory;

    private long handle;
    private Thread pollingThread;
    private final Object nativeCallLock = new Object();
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "zmusic-command");
            thread.setDaemon(true);
            return thread;
        }
    });
    private volatile float currentVolume = -1.0f;

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
        ensureNativeLibraryLoaded();
        handle = nativeInit();
        if (handle == 0) {
            throw new RuntimeException("ZMusicPlayer 初始化失败");
        }
    }

    /**
     * 设置 bundled native fallback 的提取目录。
     */
    public static void setNativeDirectory(Path nativeDirectory) {
        ZMusicPlayer.nativeDirectory = nativeDirectory;
    }

    /**
     * 销毁播放器，释放原生资源。
     */
    public synchronized void destroy() {
        running = false;
        commandExecutor.shutdownNow();
        Thread thread = pollingThread;
        if (thread != null) {
            if (thread != Thread.currentThread()) {
                thread.interrupt();
                boolean interrupted = false;
                while (thread.isAlive()) {
                    try {
                        thread.join();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            pollingThread = null;
        }
        if (handle != 0) {
            synchronized (nativeCallLock) {
                nativeDestroy(handle);
            }
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
     * 在播放器线程中停止当前曲目并播放新 URL，避免阻塞 Minecraft 客户端线程。
     */
    public void playAsync(final String url) {
        executeCommand(new Runnable() {
            @Override
            public void run() {
                if (!running || handle == 0) {
                    return;
                }
                synchronized (nativeCallLock) {
                    nativeStop(handle);
                }
                if (!running || handle == 0) {
                    return;
                }
                synchronized (nativeCallLock) {
                    nativePlay(handle, url);
                }
            }
        });
    }

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
     * 在播放器线程中停止播放。
     */
    public void stopAsync() {
        executeCommand(new Runnable() {
            @Override
            public void run() {
                if (!running || handle == 0) {
                    return;
                }
                synchronized (nativeCallLock) {
                    nativeStop(handle);
                }
            }
        });
    }

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
    public int setVolume(final float volume) {
        if (Float.compare(currentVolume, volume) == 0) {
            return 0;
        }
        currentVolume = volume;
        executeCommand(new Runnable() {
            @Override
            public void run() {
                if (!running || handle == 0) {
                    return;
                }
                synchronized (nativeCallLock) {
                    nativeSetVolume(handle, volume);
                }
            }
        });
        return 0;
    }

    private void executeCommand(Runnable command) {
        try {
            commandExecutor.execute(command);
        } catch (RejectedExecutionException ignored) {
        }
    }

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

    private volatile EventListener listener;

    /**
     * 设置事件监听器。
     */
    public void setEventListener(EventListener listener) {
        this.listener = listener;
        if (listener != null) {
            startPollingThread();
        } else {
            stopPollingThread();
        }
    }

    private synchronized void stopPollingThread() {
        Thread thread = pollingThread;
        if (thread == null) {
            return;
        }
        thread.interrupt();
        if (thread != Thread.currentThread()) {
            boolean interrupted = false;
            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (pollingThread == thread) {
            pollingThread = null;
        }
    }

    private synchronized void startPollingThread() {
        if (pollingThread != null && pollingThread.isAlive()) {
            return;
        }
        pollingThread = new Thread(() -> {
            while (running && handle != 0) {
                int event = nativePollEvent(handle);
                EventListener currentListener = listener;
                if (event != EVENT_NONE && currentListener != null) {
                    if (event == EVENT_STATE_CHANGED) {
                        currentListener.onStateChanged(nativeGetState(handle));
                    } else if (event == EVENT_TRACK_ENDED) {
                        currentListener.onTrackEnded();
                    } else if (event == EVENT_PROGRESS_UPDATE) {
                        currentListener.onProgress(nativeGetPosition(handle), nativeGetDuration(handle));
                    } else if (event == EVENT_ERROR) {
                        currentListener.onError("播放错误");
                    } else if (event == EVENT_BUFFERING) {
                        currentListener.onBuffering(true);
                    }
                }
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
            }
        });
        pollingThread.setDaemon(true);
        pollingThread.setName("zmusic-event-poll");
        pollingThread.start();
    }

    private static void ensureNativeLibraryLoaded() {
        if (nativeLibraryLoaded) {
            return;
        }
        synchronized (NATIVE_LOAD_LOCK) {
            if (nativeLibraryLoaded) {
                return;
            }
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

        String libName = getNativeLibName();
        String platform = getNativePlatform();
        String resourcePath = NATIVE_RESOURCE_ROOT + "/" + platform + "/" + libName;
        try (InputStream is = ZMusicPlayer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Native library not found in classpath: " + resourcePath);
            }
            byte[] libBytes = readAllBytes(is);
            String hash = sha256(libBytes).substring(0, 16);
            Path libDir = getNativeDirectory();
            Files.createDirectories(libDir);
            Path libFile = libDir.resolve(getHashedLibName(libName, hash));
            writeNativeLibraryIfAbsent(libDir, libFile, libBytes, hash);
            System.load(libFile.toString());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to extract native library", e);
        }
    }

    private static void writeNativeLibraryIfAbsent(Path libDir, Path libFile, byte[] libBytes, String expectedHash)
            throws IOException, NoSuchAlgorithmException {
        if (isValidNativeLibrary(libFile, expectedHash)) {
            return;
        }
        Files.deleteIfExists(libFile);

        Path tempFile = Files.createTempFile(libDir, "zmusic-", ".tmp");
        try {
            Files.write(tempFile, libBytes, StandardOpenOption.WRITE);
            try {
                Files.move(tempFile, libFile, StandardCopyOption.ATOMIC_MOVE);
            } catch (FileAlreadyExistsException ignored) {
            } catch (IOException e) {
                try {
                    Files.move(tempFile, libFile);
                } catch (FileAlreadyExistsException ignored) {
                }
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
        if (!isValidNativeLibrary(libFile, expectedHash)) {
            throw new IOException("Cached native library has unexpected content: " + libFile);
        }
    }

    private static boolean isValidNativeLibrary(Path libFile, String expectedHash)
            throws IOException, NoSuchAlgorithmException {
        return Files.exists(libFile) && sha256(Files.readAllBytes(libFile)).startsWith(expectedHash);
    }

    private static Path getNativeDirectory() {
        Path configuredDirectory = nativeDirectory;
        if (configuredDirectory != null) {
            return configuredDirectory;
        }

        Path gameDir = findGameDirectory();
        return gameDir.resolve("zmusic");
    }

    private static Path findGameDirectory() {
        Path fabricGameDir = getFabricGameDirectory();
        if (fabricGameDir != null) {
            return fabricGameDir;
        }

        Path forgeGameDir = getFmlGameDirectory("net.minecraftforge.fml.loading.FMLPaths");
        if (forgeGameDir != null) {
            return forgeGameDir;
        }

        Path neoForgeGameDir = getFmlGameDirectory("net.neoforged.fml.loading.FMLPaths");
        if (neoForgeGameDir != null) {
            return neoForgeGameDir;
        }

        Path legacyMinecraftDir = getLegacyMinecraftDirectory();
        if (legacyMinecraftDir != null) {
            return legacyMinecraftDir;
        }

        return Paths.get(System.getProperty("user.dir"));
    }

    private static Path getFabricGameDirectory() {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            Object gameDir = loaderClass.getMethod("getGameDir").invoke(loader);
            if (gameDir instanceof Path) {
                return (Path) gameDir;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Path getFmlGameDirectory(String className) {
        try {
            Class<?> pathsClass = Class.forName(className);
            Object gameDirEntry = Enum.valueOf((Class<Enum>) pathsClass.asSubclass(Enum.class), "GAMEDIR");
            Method getMethod = pathsClass.getMethod("get");
            Object gameDir = getMethod.invoke(gameDirEntry);
            if (gameDir instanceof Path) {
                return (Path) gameDir;
            }
        } catch (ReflectiveOperationException | IllegalArgumentException | LinkageError ignored) {
        }
        return null;
    }

    private static Path getLegacyMinecraftDirectory() {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getMinecraft").invoke(null);
            Object mcDataDir = minecraftClass.getField("mcDataDir").get(minecraft);
            if (mcDataDir instanceof java.io.File) {
                return ((java.io.File) mcDataDir).toPath();
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            builder.append(String.format("%02x", b & 0xff));
        }
        return builder.toString();
    }

    private static String getHashedLibName(String libName, String hash) {
        int extensionIndex = libName.lastIndexOf('.');
        if (extensionIndex <= 0) {
            return libName + "-" + hash;
        }
        return libName.substring(0, extensionIndex) + "-" + hash + libName.substring(extensionIndex);
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

        if (os.contains("linux") && "x86_64".equals(arch)) return "x86_64-linux";
        if (os.contains("win") && "x86_64".equals(arch)) return "x86_64-windows";
        if (os.contains("mac") && ("x86_64".equals(arch) || "aarch64".equals(arch))) return arch + "-macos";
        throw new UnsupportedOperationException("Unsupported native platform: " + os + " " + arch);
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
