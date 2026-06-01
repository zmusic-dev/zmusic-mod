package me.zhenxin.zmusic.mixin;

import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.network.ZMusicPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 兼容 NeoForge 1.21.1 vanilla 连接下的 ZMusic 载荷分发。
 * <p>
 * 1.21 已通过 {@link net.minecraft.network.codec.StreamCodec} 解码 custom payload，
 * NeoForge 会在通用监听器里统一处理 modded payload。Bukkit/Velocity 插件服没有
 * NeoForge 协商信息时，这里需要先消费已解码的 {@code zmusic:channel}，避免被后续
 * modded payload 校验当成未协商通道处理。
 *
 * @author 真心
 * @since 2026-04-24 20:10
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientPacketListenerPayloadMixin {

    @Inject(
            method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zmusic$handleZMusicPayload(
            ClientboundCustomPayloadPacket packet,
            CallbackInfo ci
    ) {
        CustomPacketPayload payload = packet.payload();
        if (!(payload instanceof ZMusicPayload zmusicPayload)) {
            return;
        }
        Minecraft.getInstance().execute(() -> ClientEvent.onPacket(zmusicPayload.message()));
        ci.cancel();
    }
}
