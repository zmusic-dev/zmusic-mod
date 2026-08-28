package me.zhenxin.zmusic;

import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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
 * <p>通过 JNI 桥接 Rust 播放引擎，提供完整的音乐播放器 Java 接口。
 * 包含播放控制、队列管理、歌词加载、事件回调等能力。</p>
 *
 * @author 真心
 * @since 2026-04-24 00:00
 */
@Log4j2
public class ZMusicPlayer {

    private static final String NATIVE_RESOURCE_ROOT = "META-INF/native";
    private static final Object NATIVE_LOAD_LOCK = new Object();
    private static final long ELF_PT_LOAD = 1L;
    private static final long ELF_PT_DYNAMIC = 2L;
    private static final long ELF_DT_NEEDED = 1L;
    private static final long ELF_DT_STRTAB = 5L;
    private static final long ELF_DT_SONAME = 14L;
    private static final long ELF_DT_NULL = 0L;
    private static final String BROKEN_LINUX_LIBC_STRING = "libc.so";
    private static final String FIXED_LINUX_LIBC_STRING = "libc.so.6";
    private static final byte[] BROKEN_LINUX_LIBC = "libc.so".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FIXED_LINUX_LIBC = "libc.so.6".getBytes(StandardCharsets.US_ASCII);
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
    private native String nativeGetLastError(long handle);
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
    // Minecraft 声音线程只读取快照，避免状态查询等待原生层的网络加载锁。
    private volatile int currentState = STATE_STOPPED;

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
        currentState = nativeGetState(handle);
        log.info("ZMusic native player initialized, state={}", currentState);
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
            currentState = STATE_STOPPED;
        }
    }

    /**
     * 播放指定 URL 的音频。
     *
     * @param url 音频资源的 URL
     * @return 0 表示成功，非零表示错误码
     */
    public int play(String url) {
        currentState = STATE_LOADING;
        int result = nativePlay(handle, url);
        refreshCurrentState();
        return result;
    }

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
                    int stopResult = nativeStop(handle);
                    if (stopResult != 0) {
                        log.warn("ZMusic nativeStop before play returned {}", stopResult);
                    }
                    refreshCurrentState();
                }
                if (!running || handle == 0) {
                    return;
                }
                synchronized (nativeCallLock) {
                    int stateBeforePlay = currentState;
                    currentState = STATE_LOADING;
                    log.info("Calling ZMusic nativePlay, stateBefore={}, url={}", stateBeforePlay, url);
                    int playResult = nativePlay(handle, url);
                    int stateAfterPlay = refreshCurrentState();
                    if (playResult != 0) {
                        log.warn("ZMusic nativePlay returned {} for {}, stateAfter={}, error={}",
                                playResult, url, stateAfterPlay, nativeGetLastError(handle));
                    } else {
                        log.info("ZMusic nativePlay accepted {}, stateAfter={}", url, stateAfterPlay);
                    }
                }
            }
        });
    }

    /**
     * 暂停当前播放。
     *
     * @return 0 表示成功，非零表示错误码
     */
    public int pause() {
        int result = nativePause(handle);
        refreshCurrentState();
        return result;
    }

    /**
     * 停止播放并释放音频资源。
     *
     * @return 0 表示成功，非零表示错误码
     */
    public int stop() {
        int result = nativeStop(handle);
        refreshCurrentState();
        return result;
    }

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
                    int stopResult = nativeStop(handle);
                    if (stopResult != 0) {
                        log.warn("ZMusic nativeStop returned {}", stopResult);
                    }
                    refreshCurrentState();
                }
            }
        });
    }

    /**
     * 恢复已暂停的播放。
     *
     * @return 0 表示成功，非零表示错误码
     */
    public int resume() {
        int result = nativeResume(handle);
        refreshCurrentState();
        return result;
    }

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
    public int getState() { return currentState; }

    private int refreshCurrentState() {
        if (handle == 0) {
            currentState = STATE_STOPPED;
        } else {
            currentState = nativeGetState(handle);
        }
        return currentState;
    }

    /**
     * 获取当前播放位置（毫秒）。
     */
    public long getPosition() { return nativeGetPosition(handle); }

    /**
     * 获取当前曲目的总时长（毫秒）。
     */
    public long getDuration() { return nativeGetDuration(handle); }

    /**
     * 获取最近一次播放错误名称。
     *
     * @return 原生错误名称，无错误时返回 null
     */
    public String getLastError() { return nativeGetLastError(handle); }

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
        log.info("Queue ZMusic native volume change: {}", volume);
        executeCommand(new Runnable() {
            @Override
            public void run() {
                if (!running || handle == 0) {
                    return;
                }
                synchronized (nativeCallLock) {
                    int volumeResult = nativeSetVolume(handle, volume);
                    if (volumeResult != 0) {
                        log.warn("ZMusic nativeSetVolume returned {} for {}", volumeResult, volume);
                    }
                }
            }
        });
        return 0;
    }

    private void executeCommand(Runnable command) {
        try {
            commandExecutor.execute(command);
        } catch (RejectedExecutionException ignored) {
            log.warn("ZMusic native command rejected");
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
    public void playNext() {
        currentState = STATE_LOADING;
        nativePlayNext(handle);
        refreshCurrentState();
    }

    /**
     * 跳到上一首曲目。
     */
    public void playPrevious() {
        currentState = STATE_LOADING;
        nativePlayPrevious(handle);
        refreshCurrentState();
    }

    /**
     * 跳到播放队列中指定索引的曲目并开始播放。
     */
    public void playAtIndex(int index) {
        currentState = STATE_LOADING;
        nativePlayAtIndex(handle, index);
        refreshCurrentState();
    }

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
                        currentListener.onStateChanged(refreshCurrentState());
                    } else if (event == EVENT_TRACK_ENDED) {
                        currentListener.onTrackEnded();
                    } else if (event == EVENT_PROGRESS_UPDATE) {
                        currentListener.onProgress(nativeGetPosition(handle), nativeGetDuration(handle));
                    } else if (event == EVENT_ERROR) {
                        currentState = STATE_ERROR;
                        String error = nativeGetLastError(handle);
                        currentListener.onError(error == null ? "Unknown" : error);
                        log.warn("ZMusic native player reported playback error: {}", error);
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
            log.info("Loaded ZMusic native library from java.library.path");
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
            byte[] libBytes = normalizeNativeLibrary(readAllBytes(is), platform, libName);
            String hash = sha256(libBytes).substring(0, 16);
            Path libDir = getNativeDirectory();
            Files.createDirectories(libDir);
            Path libFile = libDir.resolve(getHashedLibName(libName, hash));
            writeNativeLibraryIfAbsent(libDir, libFile, libBytes, hash);
            System.load(libFile.toString());
            log.info("Loaded bundled ZMusic native library: {}", libFile);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to extract native library", e);
        }
    }

    private static byte[] normalizeNativeLibrary(byte[] libBytes, String platform, String libName) {
        if (!"x86_64-linux".equals(platform) || !"libzmusic.so".equals(libName)) {
            return libBytes;
        }
        try {
            return patchBrokenLinuxLibcDependency(libBytes);
        } catch (RuntimeException e) {
            log.warn("Failed to normalize bundled ZMusic Linux native library, using original binary", e);
            return libBytes;
        }
    }

    private static byte[] patchBrokenLinuxLibcDependency(byte[] libBytes) {
        if (!isElf64LittleEndian(libBytes)) {
            return libBytes;
        }

        int programHeaderOffset = safeLongToInt(readLongLE(libBytes, 32));
        int programHeaderEntrySize = readUnsignedShortLE(libBytes, 54);
        int programHeaderCount = readUnsignedShortLE(libBytes, 56);

        long stringTableVirtualAddress = -1L;
        long neededStringOffset = -1L;
        long neededEntryValueOffset = -1L;
        long sonameStringOffset = -1L;

        for (int i = 0; i < programHeaderCount; i++) {
            int headerOffset = programHeaderOffset + (i * programHeaderEntrySize);
            if (headerOffset < 0 || headerOffset + 56 > libBytes.length) {
                return libBytes;
            }
            if (readIntLE(libBytes, headerOffset) != ELF_PT_DYNAMIC) {
                continue;
            }

            int dynamicOffset = safeLongToInt(readLongLE(libBytes, headerOffset + 8));
            int dynamicSize = safeLongToInt(readLongLE(libBytes, headerOffset + 32));
            for (int entryOffset = dynamicOffset; entryOffset + 16 <= dynamicOffset + dynamicSize; entryOffset += 16) {
                long tag = readLongLE(libBytes, entryOffset);
                long value = readLongLE(libBytes, entryOffset + 8);
                if (tag == ELF_DT_NULL) {
                    break;
                }
                if (tag == ELF_DT_STRTAB) {
                    stringTableVirtualAddress = value;
                } else if (tag == ELF_DT_NEEDED && neededEntryValueOffset < 0L) {
                    neededEntryValueOffset = entryOffset + 8L;
                    neededStringOffset = value;
                } else if (tag == ELF_DT_SONAME && sonameStringOffset < 0L) {
                    sonameStringOffset = value;
                }
            }
            break;
        }

        if (stringTableVirtualAddress < 0L || neededStringOffset < 0L || neededEntryValueOffset < 0L || sonameStringOffset < 0L) {
            return libBytes;
        }

        long stringTableFileOffset = findFileOffsetForVirtualAddress(
                libBytes,
                programHeaderOffset,
                programHeaderEntrySize,
                programHeaderCount,
                stringTableVirtualAddress
        );
        if (stringTableFileOffset < 0L) {
            return libBytes;
        }

        int neededFileOffset = safeLongToInt(stringTableFileOffset + neededStringOffset);
        int sonameFileOffset = safeLongToInt(stringTableFileOffset + sonameStringOffset);
        String neededLibrary = readCString(libBytes, neededFileOffset);
        if (FIXED_LINUX_LIBC_STRING.equals(neededLibrary)) {
            return libBytes;
        }
        if (!BROKEN_LINUX_LIBC_STRING.equals(neededLibrary)) {
            return libBytes;
        }

        int sonameCapacity = readCStringStorageLength(libBytes, sonameFileOffset);
        if (sonameCapacity < FIXED_LINUX_LIBC.length + 1) {
            return libBytes;
        }

        byte[] patched = libBytes.clone();
        System.arraycopy(FIXED_LINUX_LIBC, 0, patched, sonameFileOffset, FIXED_LINUX_LIBC.length);
        patched[sonameFileOffset + FIXED_LINUX_LIBC.length] = 0;
        for (int i = FIXED_LINUX_LIBC.length + 1; i < sonameCapacity; i++) {
            patched[sonameFileOffset + i] = 0;
        }
        writeLongLE(patched, safeLongToInt(neededEntryValueOffset), sonameStringOffset);
        log.info("Patched bundled ZMusic Linux native library dependency from libc.so to libc.so.6");
        return patched;
    }

    private static boolean isElf64LittleEndian(byte[] bytes) {
        return bytes.length > 6
                && bytes[0] == 0x7f
                && bytes[1] == 'E'
                && bytes[2] == 'L'
                && bytes[3] == 'F'
                && bytes[4] == 2
                && bytes[5] == 1;
    }

    private static long findFileOffsetForVirtualAddress(
            byte[] bytes,
            int programHeaderOffset,
            int programHeaderEntrySize,
            int programHeaderCount,
            long virtualAddress
    ) {
        for (int i = 0; i < programHeaderCount; i++) {
            int headerOffset = programHeaderOffset + (i * programHeaderEntrySize);
            if (headerOffset < 0 || headerOffset + 56 > bytes.length) {
                return -1L;
            }
            if (readIntLE(bytes, headerOffset) != ELF_PT_LOAD) {
                continue;
            }
            long segmentFileOffset = readLongLE(bytes, headerOffset + 8);
            long segmentVirtualAddress = readLongLE(bytes, headerOffset + 16);
            long segmentFileSize = readLongLE(bytes, headerOffset + 32);
            if (virtualAddress >= segmentVirtualAddress && virtualAddress < segmentVirtualAddress + segmentFileSize) {
                return segmentFileOffset + (virtualAddress - segmentVirtualAddress);
            }
        }
        return -1L;
    }

    private static String readCString(byte[] bytes, int offset) {
        int end = offset;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.US_ASCII);
    }

    private static int readCStringStorageLength(byte[] bytes, int offset) {
        int end = offset;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return end < bytes.length ? (end - offset) + 1 : 0;
    }

    private static int readUnsignedShortLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static int readIntLE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private static long readLongLE(byte[] bytes, int offset) {
        return ((long) bytes[offset] & 0xff)
                | (((long) bytes[offset + 1] & 0xff) << 8)
                | (((long) bytes[offset + 2] & 0xff) << 16)
                | (((long) bytes[offset + 3] & 0xff) << 24)
                | (((long) bytes[offset + 4] & 0xff) << 32)
                | (((long) bytes[offset + 5] & 0xff) << 40)
                | (((long) bytes[offset + 6] & 0xff) << 48)
                | (((long) bytes[offset + 7] & 0xff) << 56);
    }

    private static void writeLongLE(byte[] bytes, int offset, long value) {
        bytes[offset] = (byte) value;
        bytes[offset + 1] = (byte) (value >>> 8);
        bytes[offset + 2] = (byte) (value >>> 16);
        bytes[offset + 3] = (byte) (value >>> 24);
        bytes[offset + 4] = (byte) (value >>> 32);
        bytes[offset + 5] = (byte) (value >>> 40);
        bytes[offset + 6] = (byte) (value >>> 48);
        bytes[offset + 7] = (byte) (value >>> 56);
    }

    private static int safeLongToInt(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Value out of int range: " + value);
        }
        return (int) value;
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

        if (isAndroidRuntime()) {
            return Paths.get(System.getProperty("java.io.tmpdir"), "zmusic");
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

        if (isAndroidRuntime() && "aarch64".equals(arch)) {
            return "aarch64-android";
        }
        if (os.contains("linux") && ("x86_64".equals(arch) || "aarch64".equals(arch))) {
            return arch + "-linux";
        }
        if (os.contains("win") && ("x86_64".equals(arch) || "aarch64".equals(arch))) {
            return arch + "-windows";
        }
        if (os.contains("mac") && ("x86_64".equals(arch) || "aarch64".equals(arch))) return arch + "-macos";
        throw new UnsupportedOperationException("Unsupported native platform: " + os + " " + arch);
    }

    private static boolean isAndroidRuntime() {
        String osVersion = System.getProperty("os.version", "").toLowerCase(Locale.ROOT);
        return osVersion.contains("android");
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
