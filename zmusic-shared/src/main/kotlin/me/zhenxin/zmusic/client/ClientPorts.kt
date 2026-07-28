package me.zhenxin.zmusic.client

import java.nio.file.Path

/**
 * 各加载器提供给共享协议状态机的运行环境。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
data class ClientEnvironment(
    val minecraftVersion: String,
    val loader: String,
    val gameDirectory: Path,
)

/**
 * 当前服务器连接的上行插件消息出口。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
fun interface ClientTransport {
    fun send(payload: ByteArray): Boolean
}

/**
 * 由加载器实现的最小日志边界。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
interface ClientLogger {
    fun info(message: String)
    fun warn(message: String, throwable: Throwable? = null)
}

/**
 * 协议状态机使用的播放器边界，测试无需加载 JNI。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
interface PlaybackController : AutoCloseable {
    interface Listener {
        fun onStateChanged(state: Int)
        fun onTrackEnded()
        fun onProgress(positionMillis: Long, durationMillis: Long)
        fun onError(message: String)
    }

    fun setListener(listener: Listener)
    fun play(url: String): Boolean
    fun stop()
    fun loadLyrics(content: String)
    fun currentLyric(): String
    fun positionMillis(): Long
    fun durationMillis(): Long
    fun setVolume(volume: Float)
    override fun close()
}
