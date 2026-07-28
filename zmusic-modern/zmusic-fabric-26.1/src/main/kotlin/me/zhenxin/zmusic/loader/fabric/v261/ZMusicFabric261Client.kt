package me.zhenxin.zmusic.loader.fabric.v261

import me.zhenxin.zmusic.client.ClientEnvironment
import me.zhenxin.zmusic.client.ClientLogger
import me.zhenxin.zmusic.client.ZMusicClient
import me.zhenxin.zmusic.common.ZMusicConstants
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

/**
 * Fabric 26.1 客户端网络适配器。
 *
 * @author 真心
 * @since 2026-04-25 00:13
 */
class ZMusicFabric261Client : ClientModInitializer {
    override fun onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(MusicPayload.TYPE, MusicPayload.CODEC)
        PayloadTypeRegistry.serverboundPlay().register(MusicPayload.TYPE, MusicPayload.CODEC)
        ClientPlayNetworking.registerGlobalReceiver(MusicPayload.TYPE) { payload, context ->
            context.client().execute { ZMusicClient.onPacket(payload.data) }
        }
        ZMusicClient.configure(
            ClientEnvironment("26.1", "fabric", FabricLoader.getInstance().gameDir),
            { payload ->
                ClientPlayNetworking.send(MusicPayload(payload))
                true
            },
            FabricLogger,
        )
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> ZMusicClient.onConnected() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> ZMusicClient.onDisconnected() }
    }

    private data class MusicPayload(val data: ByteArray) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<MusicPayload>(
                Identifier.fromNamespaceAndPath(ZMusicConstants.MOD_ID, "packet"),
            )
            val CODEC: StreamCodec<FriendlyByteBuf, MusicPayload> = CustomPacketPayload.codec(
                { value, buffer -> buffer.writeBytes(value.data) },
                { buffer -> ByteArray(buffer.readableBytes()).also(buffer::readBytes).let(::MusicPayload) },
            )
        }
    }

    private object FabricLogger : ClientLogger {
        private val delegate = LoggerFactory.getLogger(ZMusicConstants.MOD_ID)

        override fun info(message: String) = delegate.info(message)

        override fun warn(message: String, throwable: Throwable?) {
            if (throwable == null) delegate.warn(message) else delegate.warn(message, throwable)
        }
    }
}
