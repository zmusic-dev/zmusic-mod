package me.zhenxin.zmusic.event;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.ZMusicPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.SoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge 事件
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/3/17 11:17
 */
@Log4j2
public class ForgeEvent {

    private boolean tickSyncDisabled;

    @SubscribeEvent
    public void onSound(final SoundEvent.SoundSourceEvent e) {
        if (ZMusic.getPlayer().getState() != ZMusicPlayer.STATE_PLAYING || e.getSound() == null) {
            return;
        }
        SoundSource data = e.getSound().getSource();
        //noinspection EnhancedSwitchMigration
        switch (data) {
            case MUSIC:
            case RECORDS:
                e.getChannel().stop();
                break;
            default:
        }
    }

    @SubscribeEvent
    public void onServerQuit(final ClientPlayerNetworkEvent.LoggingOut e) {
        try {
            ZMusic.getPlayer().stopAsync();
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }


    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (tickSyncDisabled) {
            return;
        }
        try {
            ZMusic.getPlayer().setVolume(ZMusic.getSoundManager().volume());
        } catch (Throwable t) {
            tickSyncDisabled = true;
            log.error("ZMusic failed to sync volume on client tick, disabling further attempts. " +
                    "This is likely caused by another mod conflicting with Minecraft's class structure.", t);
        }
    }
}
