package me.zhenxin.zmusic.loader.fabric.v261;

import me.zhenxin.zmusic.common.ZMusicConstants;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric 26.1 客户端入口。
 *
 * @author 真心
 * @since 2026-04-25 00:13
 */
public final class ZMusicFabric261Client implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZMusicConstants.MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Hello World from ZMusic Fabric 26.1!");
    }
}
