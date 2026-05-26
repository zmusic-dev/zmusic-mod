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
    private static String version = "3.1.0";

    public static void onEnable() {
        if (player != null) {
            player.destroy();
        }
        player = new ZMusicPlayer();
        registerShutdownHook();
        log.info("Welcome use ZMusic!");
        log.info("Homepage: https://m.zplu.cc");
        log.info("Github: https://github.com/zmusic-dev/zmusic-mod");
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
