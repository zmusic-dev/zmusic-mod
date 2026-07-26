package me.zhenxin.zmusic;

import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.event.ForgeEvent;
import me.zhenxin.zmusic.manager.SoundManagerImpl;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkEvent;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

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
        SimpleChannel channel = NetworkRegistry.newSimpleChannel(new ResourceLocation("zmusic", "packet"),
                () -> "1.0", s -> true, s -> true);
        channel.registerMessage('Z', byte[].class, this::enc, this::dec, this::proc);
        ClientEvent.configure(payload -> {
            channel.sendToServer(payload);
            return true;
        }, "1.15.2", "forge");
        ZMusic.onEnable();
    }

    private void enc(byte[] payload, PacketBuffer buffer) {
        buffer.writeBytes(payload, 1, payload.length - 1);
    }


    private byte[] dec(PacketBuffer buffer) {
        byte[] payload = new byte[buffer.readableBytes() + 1];
        payload[0] = 'Z';
        buffer.readBytes(payload, 1, payload.length - 1);
        return payload;
    }

    private void proc(byte[] payload, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> ClientEvent.onPacket(payload));
        context.setPacketHandled(true);
    }
}
