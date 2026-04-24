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
    @Getter
    @Setter
    private static SoundManager soundManager;
    @Getter
    private static String version = "3.1.0";

    public static void onEnable() {
        player = new ZMusicPlayer();
        log.info("Welcome use ZMusic Mod!");
        log.info("Homepage: https://m.zplu.cc");
        log.info("Github: https://github.com/RealHeart/ZMusic-Mod");
        log.info("Discord: https://discord.gg/twQgJNufYn");
        log.info("QQ Group: 1032722724");
    }
}