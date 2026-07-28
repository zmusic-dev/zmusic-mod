package me.zhenxin.zmusic.client

import me.zhenxin.zmusic.ZMusicPlayer
import java.nio.file.Path

/**
 * 将共享播放器边界连接到 zmusic-player JNI 引擎。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
class NativePlaybackController(gameDirectory: Path) : PlaybackController {
    private val player: ZMusicPlayer

    init {
        ZMusicPlayer.setNativeDirectory(gameDirectory.resolve("zmusic"))
        player = ZMusicPlayer()
    }

    override fun setListener(listener: PlaybackController.Listener) {
        player.setEventListener(object : ZMusicPlayer.EventListener {
            override fun onStateChanged(state: Int) = listener.onStateChanged(state)
            override fun onTrackEnded() = listener.onTrackEnded()
            override fun onProgress(positionMillis: Long, durationMillis: Long) =
                listener.onProgress(positionMillis, durationMillis)
            override fun onError(message: String) = listener.onError(message)
        })
    }

    override fun play(url: String) = player.playAsync(url)
    override fun stop() = player.stopAsync()
    override fun loadLyrics(content: String) = player.loadLyrics(content)
    override fun currentLyric() = player.currentLyric
    override fun positionMillis() = player.position
    override fun durationMillis() = player.duration
    override fun setVolume(volume: Float) = player.setVolume(volume)
    override fun close() = player.close()
}
