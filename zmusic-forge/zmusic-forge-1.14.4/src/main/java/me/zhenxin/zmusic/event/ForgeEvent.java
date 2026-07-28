package me.zhenxin.zmusic.event;

import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.ZMusicPlayer;
import net.minecraft.util.SoundCategory;
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
public class ForgeEvent {

    @SubscribeEvent
    public void onSound(final SoundEvent.SoundSourceEvent e) {
        if (ZMusic.getPlayer().getState() != ZMusicPlayer.STATE_PLAYING || e.getSound() == null) {
            return;
        }
        SoundCategory data = e.getSound().getCategory();
        switch (data) {
            case MUSIC:
            case RECORDS:
                e.getSource().func_216418_f();
                break;
            default:
        }
    }

    @SubscribeEvent
    public void onServerQuit(final ClientPlayerNetworkEvent.LoggedOutEvent e) {
        try {
            ZMusic.getPlayer().stopAsync();
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }


    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        ZMusic.getPlayer().setVolume(ZMusic.getSoundManager().volume());
    }
}
