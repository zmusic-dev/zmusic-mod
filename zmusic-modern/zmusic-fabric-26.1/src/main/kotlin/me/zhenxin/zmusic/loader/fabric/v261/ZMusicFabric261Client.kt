package me.zhenxin.zmusic.loader.fabric.v261

import me.zhenxin.zmusic.common.ZMusicConstants
import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory

/**
 * Fabric 26.1 客户端入口。
 *
 * @author 真心
 * @since 2026-04-25 00:13
 */
class ZMusicFabric261Client : ClientModInitializer {
    override fun onInitializeClient() {
        LOGGER.info("Hello World from ZMusic Fabric 26.1!")
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger(ZMusicConstants.MOD_ID)
    }
}
