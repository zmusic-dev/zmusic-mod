package me.zhenxin.zmusic;

import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.manager.SoundManagerImpl;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Mod 主入口
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/28 13:01
 */
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public class ZMusicMod implements ModInitializer {

    @Override
    public void onInitialize() {
        ZMusic.setSoundManager(new SoundManagerImpl());

        record MusicPayload(byte[] data) implements CustomPayload {
            public static final Id<MusicPayload> ID = new CustomPayload.Id<>(new Identifier("zmusic", "packet"));
            public static final PacketCodec<PacketByteBuf, MusicPayload> CODEC = PacketCodec.of(
                    (value, buf) -> buf.writeBytes(value.data),
                    buf -> {
                byte[] buffer = new byte[buf.readableBytes()];
                buf.readBytes(buffer);
                return new MusicPayload(buffer);
            });


            @Override
            public Id<? extends CustomPayload> getId() {
                return ID;
            }
        }

        PayloadTypeRegistry.playS2C().register(MusicPayload.ID, MusicPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MusicPayload.ID, MusicPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MusicPayload.ID, (payload, context) -> {
            context.client().execute(() -> ClientEvent.onPacket(payload.data));
        });

        ClientEvent.configure(payload -> {
            ClientPlayNetworking.send(new MusicPayload(payload));
            return true;
        }, "1.20.6", "fabric");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientEvent.onConnected());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEvent.onDisconnect());
        ZMusic.onEnable();
    }
}
