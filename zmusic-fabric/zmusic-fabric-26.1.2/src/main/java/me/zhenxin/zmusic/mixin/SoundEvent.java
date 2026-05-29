package me.zhenxin.zmusic.mixin;

import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.ZMusicPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEvent {
    @Inject(method = "play*", at = @At("HEAD"), cancellable = true)
    public void play(SoundInstance soundInstance, CallbackInfoReturnable<SoundEngine.PlayResult> info) {
        if (ZMusic.getPlayer().getState() == ZMusicPlayer.STATE_PLAYING) {
            SoundSource data = soundInstance.getSource();
            if (data == SoundSource.RECORDS || data == SoundSource.MUSIC) {
                info.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
            }
        }
    }
}
