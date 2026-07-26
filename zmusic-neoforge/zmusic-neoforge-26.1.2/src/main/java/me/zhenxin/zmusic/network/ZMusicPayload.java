package me.zhenxin.zmusic.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * ZMusic 插件消息载荷。
 * <p>
 * 载荷保存完整的 {@code ZMPK + version + JSON} 二进制协议帧。
 *
 * @author 真心
 * @since 2026-04-24 18:00
 */
public record ZMusicPayload(byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ZMusicPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("zmusic", "packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ZMusicPayload> STREAM_CODEC = StreamCodec.of(
            ZMusicPayload::write,
            ZMusicPayload::read
    );

    private static ZMusicPayload read(RegistryFriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return new ZMusicPayload(data);
    }

    private static void write(RegistryFriendlyByteBuf buf, ZMusicPayload payload) {
        buf.writeBytes(payload.data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
