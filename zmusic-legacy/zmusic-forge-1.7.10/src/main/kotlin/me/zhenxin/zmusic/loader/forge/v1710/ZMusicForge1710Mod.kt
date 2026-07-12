package me.zhenxin.zmusic.loader.forge.v1710

import cpw.mods.fml.common.Mod
import cpw.mods.fml.common.event.FMLInitializationEvent
import me.zhenxin.zmusic.common.ZMusicConstants
import org.apache.logging.log4j.LogManager

/**
 * ZMusic Forge 1.7.10 模组入口。
 *
 * @author 真心
 * @since 2026-04-25 00:13
 */
@Mod(
    modid = ZMusicConstants.MOD_ID,
    name = "ZMusic",
    version = ZMusicConstants.MOD_VERSION,
    acceptableRemoteVersions = "*",
    dependencies = "required-after:forgelin",
)
class ZMusicForge1710Mod {
    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        LOGGER.info("Hello World from ZMusic Forge 1.7.10!")
    }

    private companion object {
        val LOGGER = LogManager.getLogger("ZMusic-1.7.10")
    }
}
