package me.zhenxin.zmusic.loader.neoforge.v1211

import me.zhenxin.zmusic.common.ZMusicConstants
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import org.slf4j.LoggerFactory

/**
 * NeoForge 1.21.1 客户端入口。
 *
 * @author 真心
 * @since 2026-04-25 00:13
 */
@Mod(ZMusicConstants.MOD_ID)
class ZMusicNeoForge1211Client(modBus: IEventBus) {
    init {
        LOGGER.info("Hello World from ZMusic NeoForge 1.21.1!")
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger(ZMusicConstants.MOD_ID)
    }
}
