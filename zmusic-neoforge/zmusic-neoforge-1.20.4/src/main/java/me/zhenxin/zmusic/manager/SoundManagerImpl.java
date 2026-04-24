package me.zhenxin.zmusic.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

/**
 * 音频管理器实现
 *
 * @author 真心
 * @since 2026-04-24 11:00
 */
public class SoundManagerImpl implements SoundManager {

    @Override
    public float volume() {
        return Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS);
    }

    @Override
    public void stop() {
        Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC);
        Minecraft.getInstance().getSoundManager().stop(null, SoundSource.RECORDS);
    }
}
