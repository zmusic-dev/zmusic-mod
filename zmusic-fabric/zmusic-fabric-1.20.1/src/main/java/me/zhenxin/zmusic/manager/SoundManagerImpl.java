package me.zhenxin.zmusic.manager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;

/**
 * 音频管理器实现
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/29 23:29
 */
public class SoundManagerImpl implements SoundManager {

    @Override
    public float volume() {
        return MinecraftClient.getInstance().options.getSoundVolume(SoundCategory.RECORDS);
    }

    @Override
    public void stop() {
        MinecraftClient.getInstance().getSoundManager().stopSounds(null, SoundCategory.MUSIC);
        MinecraftClient.getInstance().getSoundManager().stopSounds(null, SoundCategory.RECORDS);
    }
}