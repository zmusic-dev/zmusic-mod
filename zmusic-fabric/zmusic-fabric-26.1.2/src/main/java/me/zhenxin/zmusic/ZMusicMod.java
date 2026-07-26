package me.zhenxin.zmusic;

import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.manager.SoundManagerImpl;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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

        record MusicPayload(byte[] data) implements CustomPacketPayload {
            public static final Type<MusicPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("zmusic", "packet"));
            public static final StreamCodec<FriendlyByteBuf, MusicPayload> STREAM_CODEC = CustomPacketPayload.codec(
                    (value, buf) -> buf.writeBytes(value.data),
                    buf -> {
                byte[] buffer = new byte[buf.readableBytes()];
                buf.readBytes(buffer);
                return new MusicPayload(buffer);
            });


            @Override
            public Type<? extends CustomPacketPayload> type() {
                return TYPE;
            }
        }

        PayloadTypeRegistry.clientboundPlay().register(MusicPayload.TYPE, MusicPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MusicPayload.TYPE, MusicPayload.STREAM_CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MusicPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> ClientEvent.onPacket(payload.data));
        });

        ClientEvent.configure(payload -> {
            ClientPlayNetworking.send(new MusicPayload(payload));
            return true;
        }, "26.1.2", "fabric");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientEvent.onConnected());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEvent.onDisconnect());
        ZMusic.onEnable();
    }
}
