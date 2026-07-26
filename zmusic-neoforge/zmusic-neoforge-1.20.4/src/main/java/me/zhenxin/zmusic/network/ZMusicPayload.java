package me.zhenxin.zmusic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * ZMusic 插件消息载荷。
 * <p>
 * 载荷保存完整的 {@code ZMPK + version + JSON} 二进制协议帧。
 *
 * @author 真心
 * @since 2026-04-24 17:50
 */
public record ZMusicPayload(byte[] data) implements CustomPacketPayload {

    public static final ResourceLocation ID = new ResourceLocation("zmusic", "packet");

    public ZMusicPayload(FriendlyByteBuf buffer) {
        this(readData(buffer));
    }

    private static byte[] readData(FriendlyByteBuf buffer) {
        byte[] data = new byte[buffer.readableBytes()];
        buffer.readBytes(data);
        return data;
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBytes(data);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
