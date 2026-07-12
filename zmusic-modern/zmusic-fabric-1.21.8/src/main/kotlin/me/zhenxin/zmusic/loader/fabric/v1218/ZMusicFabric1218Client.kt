package me.zhenxin.zmusic.loader.fabric.v1218

import me.zhenxin.zmusic.common.ZMusicConstants
import net.fabricmc.api.ClientModInitializer
import org.slf4j.LoggerFactory

/**
 * Fabric 1.21.8 客户端入口。
 *
 * @author 真心
 * @since 2026-04-25 00:13
 */
class ZMusicFabric1218Client : ClientModInitializer {
    override fun onInitializeClient() {
        LOGGER.info("Hello World from ZMusic Fabric 1.21.8!")
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger(ZMusicConstants.MOD_ID)
    }
}
