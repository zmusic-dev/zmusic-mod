package me.zhenxin.zmusic.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/**
 * ZMusic 插件消息载荷。
 * <p>
 * 服务端协议为 {@code [1字节前缀] + UTF-8 文本}，文本内容交给核心层解析。
 *
 * @author 真心
 * @since 2026-04-24 18:00
 */
public record ZMusicPayload(String message) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ZMusicPayload> TYPE =
            new CustomPacketPayload.Type<>(new ResourceLocation("zmusic", "channel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ZMusicPayload> STREAM_CODEC = StreamCodec.of(
            ZMusicPayload::write,
            ZMusicPayload::read
    );

    private static ZMusicPayload read(RegistryFriendlyByteBuf buf) {
        int readable = buf.readableBytes();
        if (readable <= 1) {
            buf.skipBytes(readable);
            return new ZMusicPayload("");
        }
        buf.skipBytes(1);
        String msg = buf.readCharSequence(buf.readableBytes(), StandardCharsets.UTF_8).toString();
        return new ZMusicPayload(msg);
    }

    private static void write(RegistryFriendlyByteBuf buf, ZMusicPayload payload) {
        buf.writeByte(666);
        buf.writeCharSequence(payload.message, StandardCharsets.UTF_8);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
