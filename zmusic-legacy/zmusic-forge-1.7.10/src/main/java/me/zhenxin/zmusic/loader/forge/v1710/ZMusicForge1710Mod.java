package me.zhenxin.zmusic.loader.forge.v1710;

import cpw.mods.fml.common.Mod;
import me.zhenxin.zmusic.common.ZMusicConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ZMusic Forge 1.7.10 模组入口。
 *
 * @author 真心
 * @since 2026-04-25 00:13
 */
@Mod(
        modid = ZMusicConstants.MOD_ID,
        name = "ZMusic",
        version = ZMusicForge1710Tags.VERSION,
        acceptableRemoteVersions = "*"
)
public final class ZMusicForge1710Mod {
    public static final Logger LOGGER = LogManager.getLogger("ZMusic-1.7.10");

    @Mod.EventHandler
    @SuppressWarnings("unused")
    public void init(cpw.mods.fml.common.event.FMLInitializationEvent event) {
        LOGGER.info("Hello World from ZMusic Forge 1.7.10!");
    }
}
