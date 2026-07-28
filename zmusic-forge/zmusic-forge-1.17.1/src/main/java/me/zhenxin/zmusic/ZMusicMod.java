package me.zhenxin.zmusic;

import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.event.ForgeEvent;
import me.zhenxin.zmusic.manager.SoundManagerImpl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fmllegacy.network.NetworkEvent;
import net.minecraftforge.fmllegacy.network.NetworkRegistry;
import net.minecraftforge.fmllegacy.network.simple.SimpleChannel;

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

/**
 * Mod 主入口
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/28 13:01
 */
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
@Mod("zmusic")
public class ZMusicMod {
    public ZMusicMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(new ForgeEvent());
    }

    private void setup(FMLClientSetupEvent event) {
        ZMusic.setSoundManager(new SoundManagerImpl());
        SimpleChannel channel = NetworkRegistry.newSimpleChannel(new ResourceLocation("zmusic", "channel"),
                () -> "1.0", s -> true, s -> true);
        channel.registerMessage(666, String.class, this::enc, this::dec, this::proc);
        ZMusic.onEnable();
    }

    private void enc(String str, FriendlyByteBuf buffer) {
        buffer.writeBytes(str.getBytes(StandardCharsets.UTF_8));
    }


    private String dec(FriendlyByteBuf buffer) {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private void proc(String message, Supplier<NetworkEvent.Context> supplier) {
        ClientEvent.onPacket(message);
        NetworkEvent.Context context = supplier.get();
        context.setPacketHandled(true);
    }
}