package me.zhenxin.zmusic.loader.forge.v1122

import io.netty.buffer.Unpooled
import me.zhenxin.zmusic.client.ClientEnvironment
import me.zhenxin.zmusic.client.ClientLogger
import me.zhenxin.zmusic.client.ZMusicClient
import me.zhenxin.zmusic.common.ZMusicConstants
import net.minecraft.client.Minecraft
import net.minecraft.network.PacketBuffer
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLEventChannel
import net.minecraftforge.fml.common.network.FMLNetworkEvent
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket
import org.apache.logging.log4j.LogManager

/**
 * ZMusic Forge 1.12.2 客户端网络适配器。
 *
 * @author 真心
 * @since 2026-07-28 00:00
 */
@Mod(
    modid = ZMusicConstants.MOD_ID,
    name = "ZMusic",
    version = ZMusicConstants.MOD_VERSION,
    acceptableRemoteVersions = "*",
    dependencies = "required-after:forgelin",
)
class ZMusicForge1122Mod {
    private lateinit var channel: FMLEventChannel

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        channel = NetworkRegistry.INSTANCE.newEventDrivenChannel(ZMusicConstants.CHANNEL)
        channel.register(this)
        MinecraftForge.EVENT_BUS.register(this)
        ZMusicClient.configure(
            ClientEnvironment("1.12.2", "forge", Minecraft.getMinecraft().gameDir.toPath()),
            { payload ->
                channel.sendToServer(
                    FMLProxyPacket(PacketBuffer(Unpooled.wrappedBuffer(payload)), ZMusicConstants.CHANNEL),
                )
                true
            },
            ForgeLogger,
        )
    }

    @SubscribeEvent
    fun onPacket(event: FMLNetworkEvent.ClientCustomPacketEvent) {
        val buffer = event.packet.payload()
        val payload = ByteArray(buffer.readableBytes())
        buffer.getBytes(buffer.readerIndex(), payload)
        Minecraft.getMinecraft().addScheduledTask { ZMusicClient.onPacket(payload) }
    }

    @SubscribeEvent
    fun onConnected(event: FMLNetworkEvent.ClientConnectedToServerEvent) = ZMusicClient.onConnected()

    @SubscribeEvent
    fun onDisconnected(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) = ZMusicClient.onDisconnected()

    private object ForgeLogger : ClientLogger {
        private val delegate = LogManager.getLogger(ZMusicConstants.MOD_ID)

        override fun info(message: String) = delegate.info(message)

        override fun warn(message: String, throwable: Throwable?) {
            if (throwable == null) delegate.warn(message) else delegate.warn(message, throwable)
        }
    }
}
