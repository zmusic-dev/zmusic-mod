package me.zhenxin.zmusic;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.manager.SoundManager;


/**
 * ZMusic 主入口
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/28 13:08
 */
@SuppressWarnings({"AlibabaClassNamingShouldBeCamel", "AlibabaConstantFieldShouldBeUpperCase"})
@Log4j2
public class ZMusic {
    @Getter
    private static ZMusicPlayer player;
    private static boolean shutdownHookRegistered;
    @Getter
    @Setter
    private static SoundManager soundManager;
    @Getter
    private static String version = "3.8.3";

    public static void onEnable() {
        if (player != null) {
            player.destroy();
        }
        player = new ZMusicPlayer();
        player.setEventListener(new ZMusicPlayer.EventListener() {
            @Override
            public void onStateChanged(int state) {
                log.info("ZMusic native player state changed: {}", state);
            }

            @Override
            public void onTrackEnded() {
                log.info("ZMusic native track ended");
            }

            @Override
            public void onProgress(long positionMs, long durationMs) {
            }

            @Override
            public void onError(String message) {
                log.warn("ZMusic native player error: {}", message);
            }

            @Override
            public void onBuffering(boolean buffering) {
                log.info("ZMusic native player buffering: {}", buffering);
            }
        });
        registerShutdownHook();
        log.info("Welcome use ZMusic!");
        log.info("Homepage: https://m.zplu.cc");
        log.info("Github: https://github.com/starhui-dev/zmusic-mod");
        log.info("Discord: https://discord.gg/twQgJNufYn");
        log.info("QQ Group: 1032722724");
    }

    public static void onDisable() {
        if (player != null) {
            player.destroy();
            player = null;
        }
    }

    private static void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(ZMusic::onDisable, "zmusic-shutdown"));
        shutdownHookRegistered = true;
    }
}
