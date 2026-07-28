package me.zhenxin.zmusic.loader.neoforge.v1211

import me.zhenxin.zmusic.client.ClientEnvironment
import me.zhenxin.zmusic.client.ClientLogger
import me.zhenxin.zmusic.client.ZMusicClient
import me.zhenxin.zmusic.common.ZMusicConstants
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.slf4j.LoggerFactory

/**
 * NeoForge 1.21.1 客户端网络适配器。
 *
 * @author 真心
 * @since 2026-04-25 00:13
 */
@Mod(ZMusicConstants.MOD_ID)
class ZMusicNeoForge1211Client(modBus: IEventBus) {
    init {
        ZMusicClient.configure(
            ClientEnvironment("1.21.1", "neoforge", FMLPaths.GAMEDIR.get()),
            { payload ->
                PacketDistributor.sendToServer(MusicPayload(payload))
                true
            },
            NeoForgeLogger,
        )
        modBus.addListener(::registerPayloads)
        NeoForge.EVENT_BUS.addListener(::onLogin)
        NeoForge.EVENT_BUS.addListener(::onLogout)
    }

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        event.registrar(PROTOCOL_REGISTRATION_VERSION)
            .optional()
            .playBidirectional(MusicPayload.TYPE, MusicPayload.CODEC) { payload, context ->
                context.enqueueWork { ZMusicClient.onPacket(payload.data) }
            }
    }

    private fun onLogin(event: ClientPlayerNetworkEvent.LoggingIn) = ZMusicClient.onConnected()

    private fun onLogout(event: ClientPlayerNetworkEvent.LoggingOut) = ZMusicClient.onDisconnected()

    private data class MusicPayload(val data: ByteArray) : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<MusicPayload>(
                ResourceLocation.fromNamespaceAndPath(ZMusicConstants.MOD_ID, "packet"),
            )
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, MusicPayload> = StreamCodec.of(
                { buffer, value -> buffer.writeBytes(value.data) },
                { buffer -> ByteArray(buffer.readableBytes()).also(buffer::readBytes).let(::MusicPayload) },
            )
        }
    }

    private object NeoForgeLogger : ClientLogger {
        private val delegate = LoggerFactory.getLogger(ZMusicConstants.MOD_ID)

        override fun info(message: String) = delegate.info(message)

        override fun warn(message: String, throwable: Throwable?) {
            if (throwable == null) delegate.warn(message) else delegate.warn(message, throwable)
        }
    }

    private companion object {
        const val PROTOCOL_REGISTRATION_VERSION = "1"
    }
}
