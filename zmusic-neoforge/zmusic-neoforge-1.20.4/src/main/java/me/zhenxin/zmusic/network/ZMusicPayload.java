package me.zhenxin.zmusic.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

/**
 * ZMusic 插件消息载荷。
 * <p>
 * 服务端协议为 {@code [1字节前缀] + UTF-8 文本}，文本内容交给核心层解析。
 *
 * @author 真心
 * @since 2026-04-24 17:50
 */
public record ZMusicPayload(String message) implements CustomPacketPayload {

    public static final ResourceLocation ID = new ResourceLocation("zmusic", "channel");

    public ZMusicPayload(FriendlyByteBuf buffer) {
        this(readMessage(buffer));
    }

    private static String readMessage(FriendlyByteBuf buffer) {
        int readable = buffer.readableBytes();
        if (readable <= 1) {
            buffer.skipBytes(readable);
            return "";
        }
        buffer.skipBytes(1);
        return buffer.readCharSequence(buffer.readableBytes(), StandardCharsets.UTF_8).toString();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(666);
        buffer.writeCharSequence(message, StandardCharsets.UTF_8);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
