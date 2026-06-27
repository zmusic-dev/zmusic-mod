package me.zhenxin.zmusic.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

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
        try {
            return Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS);
        } catch (LinkageError e) {
            return MinecraftSoundAccess.getRecordsVolume();
        }
    }

    @Override
    public void stop() {
        try {
            Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC);
            Minecraft.getInstance().getSoundManager().stop(null, SoundSource.RECORDS);
        } catch (LinkageError e) {
            MinecraftSoundAccess.stopMusicAndRecords();
        }
    }
}