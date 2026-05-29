package me.zhenxin.zmusic.mixin;

import me.zhenxin.zmusic.ZMusic;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class Tick {

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick(CallbackInfo info) {
        ZMusic.getPlayer().setVolume(ZMusic.getSoundManager().volume());
    }
}
