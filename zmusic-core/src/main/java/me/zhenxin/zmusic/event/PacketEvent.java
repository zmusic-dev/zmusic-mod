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
        ZMusic.getSoundManager().stop();
        ZMusic.getPlayer().playAsync(data);
    }

    public static void onStop() {
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
