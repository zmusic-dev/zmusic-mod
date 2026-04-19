package me.zhenxin.zmusic.player;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.player.decoder.BuffPack;
import me.zhenxin.zmusic.player.decoder.IDecoder;
import me.zhenxin.zmusic.player.decoder.flac.FlacDecoder;
import me.zhenxin.zmusic.player.decoder.mp3.Mp3Decoder;
import me.zhenxin.zmusic.player.decoder.ogg.OggDecoder;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"AlibabaClassMustHaveAuthor", "AlibabaClassNamingShouldBeCamel", "AlibabaAvoidManuallyCreateThread", "AlibabaUndefineMagicConstant", "HttpUrlsUsage", "AlibabaLowerCamelCaseVariableNaming", "NullableProblems"})
@Log4j2
public class MusicPlayer extends InputStream {
    private static final int MAX_DECODED_BUFFERS = 16;
    private static final int MAX_OPENAL_BUFFERS = 8;
    // 至少预缓冲半个 OpenAL 队列再开播，降低刚启动时的断粮概率。
    private static final int MIN_START_OPENAL_BUFFERS = Math.max(1, (MAX_OPENAL_BUFFERS + 1) / 2);

    private HttpURLConnection connection;
    private volatile String url;
    private InputStream content;

    private volatile boolean isClose = false;
    private volatile boolean reload = false;
    private IDecoder decoder;
    private final Queue<String> urls = new ConcurrentLinkedQueue<>();
    private volatile int time = 0;
    private volatile long local = 0;
    private final Semaphore semaphore = new Semaphore(0);
    private final Semaphore semaphore1 = new Semaphore(0);
    private final BlockingQueue<ByteBuffer> queue = new LinkedBlockingQueue<>(MAX_DECODED_BUFFERS);
    private volatile boolean isPlay = false;
    private volatile boolean wait = false;
    private volatile boolean decodeCompleted = false;
    private volatile int index;
    private volatile int frequency;
    private volatile int channels;

    public MusicPlayer() {
        try {
            new Thread(this::run, "zmusic-player-thread").start();
            ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
            service.scheduleAtFixedRate(this::run1, 0, 10, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void run1() {
        if (isPlay) {
            time += 10;
        }
    }

    public boolean isPlay() {
        return isPlay;
    }

    public static URL get(URL url) {
        if (url.toString().contains("https://music.163.com/song/media/outer/url?id=")
                || url.toString().contains("http://music.163.com/song/media/outer/url?id=")) {
            try {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(4 * 1000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/84.0.4147.105 Safari/537.36 Edg/84.0.522.52");
                connection.setRequestProperty("Host", "music.163.com");
                connection.connect();
                if (connection.getResponseCode() == 302) {
                    return new URL(connection.getHeaderField("Location"));
                }
                return connection.getURL();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return url;
    }

    public void set(String time) {
        try {
            int time1 = Integer.parseInt(time);
            set(time1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void set(int time) {
        closePlayer();
        this.time = time;
        urls.add(url);
        semaphore.release();
    }

    public void connect() throws IOException {
        streamClose();
        URL urlObject = new URL(url);
        connection = (HttpURLConnection) urlObject.openConnection();
        connection.setRequestProperty("Range", "bytes=" + local + "-");
        connection.setRequestProperty("User-Agent", "ZMusic Mod/" + ZMusic.getVersion());
        connection.connect();

        int responseCode = connection.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            content = new BufferedInputStream(connection.getInputStream());
        } else {
            throw new IOException("Failed to connect, response code: " + responseCode);
        }
    }

    @SuppressWarnings("AlibabaMethodTooLong")
    private void run() {
        while (true) {
            try {
                semaphore.acquire();
                url = urls.poll();
                if (url == null || url.isEmpty()) {
                    continue;
                }
                urls.clear();
                URL nowURL = new URL(url);
                nowURL = get(nowURL);
                if (nowURL == null) {
                    continue;
                }
                try {
                    local = 0;
                    connect();
                } catch (Exception e) {
                    e.printStackTrace();
                    log.warn("Failed to get the music!");
                    continue;
                }

                decoder = new FlacDecoder(this);
                if (!decoder.set()) {
                    local = 0;
                    connect();
                    decoder = new OggDecoder(this);
                    if (!decoder.set()) {
                        local = 0;
                        connect();
                        decoder = new Mp3Decoder(this);
                        if (!decoder.set()) {
                            log.warn("An unsupported file format!");
                            continue;
                        }
                    }
                }

                index = AL10.alGenSources();
                int m_numqueued = AL10.alGetSourcei(index, AL10.AL_BUFFERS_QUEUED);
                while (m_numqueued > 0) {
                    int temp = AL10.alSourceUnqueueBuffers(index);
                    AL10.alDeleteBuffers(temp);
                    m_numqueued--;
                }
                frequency = decoder.getOutputFrequency();
                channels = decoder.getOutputChannels();
                if (channels != 1 && channels != 2) {
                    continue;
                }
                if (time != 0) {
                    decoder.set(time);
                }
                queue.clear();
                reload = false;
                isClose = false;
                decodeCompleted = false;
                isPlay = true;
                while (true) {
                    try {
                        if (isClose) {
                            break;
                        }
                        BuffPack output = decoder.decodeFrame();
                        if (output == null) {
                            break;
                        }
                        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(
                                output.len).put(output.buff, 0, output.len);
                        ((Buffer) byteBuffer).flip();
                        if (!offerDecodedBuffer(byteBuffer)) {
                            break;
                        }
                    } catch (Exception e) {
                        if (!isClose) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
                decodeCompleted = true;
                streamClose();
                decodeClose();
                while (!isClose && AL10.alGetSourcei(index,
                        AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
                    AL10.alSourcef(index, AL10.AL_GAIN, ZMusic.getSoundManager().volume());
                    //noinspection BusyWait
                    Thread.sleep(100);
                }
                if (!reload) {
                    wait = true;
                    if (semaphore1.tryAcquire(500, TimeUnit.MILLISECONDS)) {
                        if (reload) {
                            urls.add(url);
                            semaphore.release();
                            continue;
                        }
                    }
                    isPlay = false;
                    AL10.alSourceStop(index);
                    m_numqueued = AL10.alGetSourcei(index, AL10.AL_BUFFERS_QUEUED);
                    while (m_numqueued > 0) {
                        int temp = AL10.alSourceUnqueueBuffers(index);
                        AL10.alDeleteBuffers(temp);
                        m_numqueued--;
                    }
                    AL10.alDeleteSources(index);
                } else {
                    urls.add(url);
                    semaphore.release();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void tick() {
        if (wait) {
            wait = false;
            semaphore1.release();
        }
        if (isClose) {
            queue.clear();
            return;
        }
        if (!isPlay) {
            return;
        }
        int queued = recycleProcessedBuffers();
        while (queued < MAX_OPENAL_BUFFERS) {
            ByteBuffer byteBuffer = queue.poll();
            if (byteBuffer == null) {
                break;
            }
            if (isClose) {
                return;
            }
            IntBuffer intBuffer = BufferUtils.createIntBuffer(1);
            AL10.alGenBuffers(intBuffer);

            AL10.alBufferData(intBuffer.get(0), channels == 1
                    ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, byteBuffer, frequency);
            AL10.alSourcef(index, AL10.AL_GAIN, ZMusic.getSoundManager().volume());

            AL10.alSourceQueueBuffers(index, intBuffer);
            queued++;
        }
        if (queued > 0 && shouldStartPlayback(queued) && AL10.alGetSourcei(index,
                AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(index);
        }
    }

    public void closePlayer() {
        isClose = true;
    }

    public void setMusic(String url) {
        time = 0;
        closePlayer();
        urls.add(url);
        semaphore.release();
    }

    private void streamClose() throws IOException {
        if (content != null) {
            content.close();
            content = null;
        }
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
    }

    private void decodeClose() throws Exception {
        if (decoder != null) {
            decoder.close();
            decoder = null;
        }
    }


    @Override
    public int read() throws IOException {
        return content.read();
    }

    @Override
    public int read(byte[] buf) throws IOException {
        return content.read(buf);
    }

    @Override
    public synchronized int read(byte[] buf, int off, int len)
            throws IOException {
        try {
            int temp = content.read(buf, off, len);
            local += temp;
            return temp;
        } catch (IOException ex) {
            connect();
            return read(buf, off, len);
        }
    }

    @Override
    public synchronized int available() throws IOException {
        return content.available();
    }

    @Override
    public void close() throws IOException {
        streamClose();
    }

    public void setLocal(long local) throws IOException {
        streamClose();
        this.local = local;
        connect();
    }

    public void setReload() {
        if (isPlay) {
            reload = true;
            isClose = true;
        }
    }

    private boolean offerDecodedBuffer(ByteBuffer byteBuffer) throws InterruptedException {
        while (!isClose) {
            if (queue.offer(byteBuffer, 100, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
    }

    private int recycleProcessedBuffers() {
        int queued = AL10.alGetSourcei(index, AL10.AL_BUFFERS_QUEUED);
        int processed = AL10.alGetSourcei(index, AL10.AL_BUFFERS_PROCESSED);
        while (processed > 0) {
            int temp = AL10.alSourceUnqueueBuffers(index);
            AL10.alDeleteBuffers(temp);
            processed--;
            queued--;
        }
        return Math.max(queued, 0);
    }

    private boolean shouldStartPlayback(int queued) {
        return queued >= MIN_START_OPENAL_BUFFERS || decodeCompleted;
    }
}
