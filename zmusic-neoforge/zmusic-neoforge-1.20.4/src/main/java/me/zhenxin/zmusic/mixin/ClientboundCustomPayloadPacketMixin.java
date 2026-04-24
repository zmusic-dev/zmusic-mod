package me.zhenxin.zmusic.mixin;

import me.zhenxin.zmusic.event.ClientEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.charset.StandardCharsets;

/**
 * 拦截 vanilla 服务端发来的 zmusic:channel custom payload。
 * <p>
 * NeoForge 1.20.4 在连接 vanilla/Bukkit/Velocity 服务端时，RegisterPayloadHandlerEvent
 * 的 handler 不执行（connectionType.isNotVanilla() == false），导致 zmusic:channel
 * 走到 readUnknownPayload → DiscardedPayload，数据被 skipBytes 丢弃。
 * <p>
 * 本 mixin 在 readUnknownPayload 的 HEAD 处拦截：对 zmusic:channel 通道，
 * 先读取原始字节、跳过服务端协议首字节、按 UTF-8 解码正文，再调度到主线程
 * 调用 ClientEvent.onPacket(...)，最后让原方法 skip 0 字节正常返回 DiscardedPayload。
 *
 * @author 真心
 * @since 2026-04-24 13:00
 */
@Mixin(net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.class)
public abstract class ClientboundCustomPayloadPacketMixin {

    private static final ResourceLocation ZMUSIC_CHANNEL = new ResourceLocation("zmusic", "channel");

    @Inject(method = "readUnknownPayload", at = @At("HEAD"), cancellable = true)
    private static void zmusic$interceptVanillaPayload(
            ResourceLocation id,
            FriendlyByteBuf buf,
            CallbackInfoReturnable<DiscardedPayload> cir
    ) {
        if (!ZMUSIC_CHANNEL.equals(id)) {
            return;
        }
        int readable = buf.readableBytes();
        if (readable > 1048576) {
            return;
        }
        if (readable <= 1) {
            buf.skipBytes(readable);
            cir.setReturnValue(new DiscardedPayload(id));
            return;
        }
        byte[] data = new byte[readable];
        buf.readBytes(data);
        // 服务端协议: [1字节前缀] + UTF-8 JSON，跳过首字节
        String message = new String(data, 1, data.length - 1, StandardCharsets.UTF_8);
        // 网络线程 → 主线程
        Minecraft.getInstance().execute(() -> ClientEvent.onPacket(message));
        cir.setReturnValue(new DiscardedPayload(id));
    }
}
