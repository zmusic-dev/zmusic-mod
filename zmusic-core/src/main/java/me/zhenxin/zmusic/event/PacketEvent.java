package me.zhenxin.zmusic.event;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;


/**
 * 发包事件
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/29 22:50
 */
@Log4j2
class PacketEvent {

    public static void onPlay(String data) {
        log.info("Play music from {}", data);
        if (data == null || data.trim().isEmpty()) {
            log.warn("Ignored empty ZMusic play url");
            return;
        }
        if (ZMusic.getSoundManager() == null) {
            log.warn("ZMusic SoundManager is not initialized");
        } else {
            log.info("Stopping vanilla music before ZMusic playback");
            ZMusic.getSoundManager().stop();
        }
        if (ZMusic.getPlayer() == null) {
            log.warn("ZMusic player is not initialized");
            return;
        }
        ZMusic.getPlayer().playAsync(data);
    }

    public static void onStop() {
        log.info("Stop ZMusic playback");
        if (ZMusic.getPlayer() == null) {
            log.warn("ZMusic player is not initialized");
            return;
        }
        ZMusic.getPlayer().stopAsync();
    }

    public static void onLyric(String data) {
        // TODO: 歌词
    }

    public static void onInfo(String data) {
        // TODO: 信息
    }

    public static void onImg(String data) {
        // TODO: 专辑图片
    }
}
