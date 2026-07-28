package me.zhenxin.zmusic.loader.fabric.v1165

import me.zhenxin.zmusic.client.ClientEnvironment
import me.zhenxin.zmusic.client.ClientLogger
import me.zhenxin.zmusic.client.ZMusicClient
import me.zhenxin.zmusic.common.ZMusicConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.Identifier
import org.apache.logging.log4j.LogManager

/**
 * Fabric 1.16.5 客户端网络适配器。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
class ZMusicFabric1165Client : ClientModInitializer {
    override fun onInitializeClient() {
        ZMusicClient.configure(
            ClientEnvironment("1.16.5", "fabric", FabricLoader.getInstance().gameDir),
            { payload ->
                val buffer = PacketByteBufs.create()
                buffer.writeBytes(payload)
                ClientPlayNetworking.send(CHANNEL, buffer)
                true
            },
            FabricLogger,
        )
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL) { client, _, buffer, _ ->
            val payload = ByteArray(buffer.readableBytes())
            buffer.readBytes(payload)
            client.execute { ZMusicClient.onPacket(payload) }
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> ZMusicClient.onConnected() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> ZMusicClient.onDisconnected() }
    }

    private object FabricLogger : ClientLogger {
        private val delegate = LogManager.getLogger(ZMusicConstants.MOD_ID)

        override fun info(message: String) = delegate.info(message)

        override fun warn(message: String, throwable: Throwable?) {
            if (throwable == null) delegate.warn(message) else delegate.warn(message, throwable)
        }
    }

    private companion object {
        val CHANNEL = Identifier(ZMusicConstants.MOD_ID, "packet")
    }
}
