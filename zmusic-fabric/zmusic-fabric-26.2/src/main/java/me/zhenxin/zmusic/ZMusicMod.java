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

import java.nio.charset.StandardCharsets;

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

        record MusicPayload(String str) implements CustomPacketPayload {
            public static final Type<MusicPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("zmusic", "channel"));
            public static final StreamCodec<FriendlyByteBuf, MusicPayload> STREAM_CODEC = CustomPacketPayload.codec((value, buf) -> {
            }, buf -> {
                byte[] buffer = new byte[buf.readableBytes()];
                buf.readBytes(buffer);
                buffer[0] = 0;
                String message = new String(buffer, StandardCharsets.UTF_8).substring(1);
                return new MusicPayload(message);
            });


            @Override
            public Type<? extends CustomPacketPayload> type() {
                return TYPE;
            }
        }

        PayloadTypeRegistry.clientboundPlay().register(MusicPayload.TYPE, MusicPayload.STREAM_CODEC);
        ClientPlayNetworking.registerGlobalReceiver(MusicPayload.TYPE, (payload, context) -> {
            ClientEvent.onPacket(payload.str);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEvent.onDisconnect());
        ZMusic.onEnable();
    }
}
