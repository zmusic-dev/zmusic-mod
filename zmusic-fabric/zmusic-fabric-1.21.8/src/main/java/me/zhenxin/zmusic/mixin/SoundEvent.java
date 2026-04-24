package me.zhenxin.zmusic.mixin;

import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.ZMusicPlayer;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public class SoundEvent {
    @Inject(method = "play*", at = @At("HEAD"), cancellable = true)
    public void play(SoundInstance soundInstance, CallbackInfoReturnable<SoundSystem.PlayResult> info) {
        if (ZMusic.getPlayer().getState() == ZMusicPlayer.STATE_PLAYING) {
            SoundCategory data = soundInstance.getCategory();
            if (data == SoundCategory.RECORDS || data == SoundCategory.MUSIC) {
                info.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
            }
        }
    }
}