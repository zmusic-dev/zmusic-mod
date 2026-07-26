package me.zhenxin.zmusic.mixin;

import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.network.ZMusicPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 兼容 NeoForge 1.20.4 vanilla 连接下不分发 optional payload handler 的行为。
 * <p>
 * Bukkit/Velocity 插件服会被 NeoForge 识别为 vanilla connection，原逻辑会跳过
 * {@code NetworkRegistry.onModdedPacketAtClient(...)}，导致已解码的 {@code zmusic:packet}
 * 最终进入 vanilla unknown 分支。本 mixin 在该分支前消费 ZMusic 载荷。
 *
 * @author 真心
 * @since 2026-04-24 17:55
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerPayloadMixin {

    @Inject(
            method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void zmusic$handleZMusicPayload(
            ClientboundCustomPayloadPacket packet,
            CustomPacketPayload payload,
            CallbackInfo ci
    ) {
        if (!(payload instanceof ZMusicPayload zmusicPayload)) {
            return;
        }
        Minecraft.getInstance().execute(() -> ClientEvent.onPacket(zmusicPayload.data()));
        ci.cancel();
    }
}
