package me.zhenxin.zmusic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.event.ForgeEvent;
import me.zhenxin.zmusic.manager.SoundManagerImpl;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.common.network.NetworkRegistry;

/**
 * Mod 主入口
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/28 13:01
 */
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
@Mod(modid = "zmusic", version = "3.7.0", acceptedMinecraftVersions = "[1.12,)")
public class ZMusicMod {
    private FMLEventChannel channel;

    @Mod.EventHandler
    private void onPreInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ForgeEvent());
        channel = NetworkRegistry.INSTANCE.newEventDrivenChannel("zmusic:packet");
        channel.register(this);
    }

    @Mod.EventHandler
    public void onPostInit(FMLPostInitializationEvent event) {
        ZMusic.setSoundManager(new SoundManagerImpl());
        ClientEvent.configure(payload -> {
            channel.sendToServer(new FMLProxyPacket(new PacketBuffer(Unpooled.wrappedBuffer(payload)), "zmusic:packet"));
            return true;
        }, "1.12.2", "forge");
        ZMusic.onEnable();
    }

    @SubscribeEvent
    public void onClientPacket(final FMLNetworkEvent.ClientCustomPacketEvent evt) {
        final ByteBuf directBuf = evt.getPacket().payload();
        byte[] array = new byte[directBuf.readableBytes()];
        directBuf.getBytes(directBuf.readerIndex(), array);
        Minecraft.getMinecraft().addScheduledTask(() -> ClientEvent.onPacket(array));
    }
}
