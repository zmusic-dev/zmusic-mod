package me.zhenxin.zmusic;

import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.event.ForgeEvent;
import me.zhenxin.zmusic.manager.SoundManagerImpl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;

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
        SimpleChannel channel = ChannelBuilder.named(new ResourceLocation("zmusic", "packet"))
                .networkProtocolVersion(1)
                .optional()
                .simpleChannel();
        channel.messageBuilder(byte[].class, 'Z')
                .encoder(this::enc)
                .decoder(this::dec)
                .consumerMainThread(this::proc)
                .add();
        ClientEvent.configure(payload -> {
            channel.send(payload, net.minecraftforge.network.PacketDistributor.SERVER.noArg());
            return true;
        }, "1.20.4", "forge");
        ZMusic.onEnable();
    }

    private void enc(byte[] payload, FriendlyByteBuf buffer) {
        buffer.writeBytes(payload, 1, payload.length - 1);
    }


    private byte[] dec(FriendlyByteBuf buffer) {
        byte[] payload = new byte[buffer.readableBytes() + 1];
        payload[0] = 'Z';
        buffer.readBytes(payload, 1, payload.length - 1);
        return payload;
    }

    private void proc(byte[] payload, CustomPayloadEvent.Context context) {
        ClientEvent.onPacket(payload);
        context.setPacketHandled(true);
    }
}
