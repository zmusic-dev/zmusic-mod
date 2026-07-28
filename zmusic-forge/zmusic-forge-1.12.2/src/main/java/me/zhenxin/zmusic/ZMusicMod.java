package me.zhenxin.zmusic;

import io.netty.buffer.ByteBuf;
import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.event.ForgeEvent;
import me.zhenxin.zmusic.manager.SoundManagerImpl;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import java.nio.charset.StandardCharsets;

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

    @Mod.EventHandler
    private void onPreInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ForgeEvent());
        NetworkRegistry.INSTANCE.newEventDrivenChannel("zmusic:channel").register(this);
    }

    @Mod.EventHandler
    public void onPostInit(FMLPostInitializationEvent event) {
        ZMusic.setSoundManager(new SoundManagerImpl());
        ZMusic.onEnable();
    }

    @SubscribeEvent
    public void onClientPacket(final FMLNetworkEvent.ClientCustomPacketEvent evt) {
        final ByteBuf directBuf = evt.getPacket().payload();
        byte[] array = new byte[directBuf.readableBytes()];
        directBuf.getBytes(directBuf.readerIndex(), array);
        array[0] = 0;
        String message = new String(array, StandardCharsets.UTF_8).substring(1);
        ClientEvent.onPacket(message);
    }
}
