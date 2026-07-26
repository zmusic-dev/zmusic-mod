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

/**
 * 拦截 vanilla 服务端发来的 zmusic:packet custom payload。
 * <p>
 * NeoForge 1.20.4 在连接 vanilla/Bukkit/Velocity 服务端时，RegisterPayloadHandlerEvent
 * 的 handler 不执行（connectionType.isNotVanilla() == false），导致 zmusic:packet
 * 走到 readUnknownPayload → DiscardedPayload，数据被 skipBytes 丢弃。
 * <p>
 * 本 mixin 在 readUnknownPayload 的 HEAD 处拦截：对 zmusic:packet 通道，
 * 读取完整协议帧后调度到主线程
 * 调用 ClientEvent.onPacket(...)，最后让原方法 skip 0 字节正常返回 DiscardedPayload。
 *
 * @author 真心
 * @since 2026-04-24 13:00
 */
@Mixin(net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket.class)
public abstract class ClientboundCustomPayloadPacketMixin {

    private static final ResourceLocation ZMUSIC_CHANNEL = new ResourceLocation("zmusic", "packet");

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
        if (readable == 0) {
            buf.skipBytes(readable);
            cir.setReturnValue(new DiscardedPayload(id));
            return;
        }
        byte[] data = new byte[readable];
        buf.readBytes(data);
        // 网络线程 → 主线程
        Minecraft.getInstance().execute(() -> ClientEvent.onPacket(data));
        cir.setReturnValue(new DiscardedPayload(id));
    }
}
