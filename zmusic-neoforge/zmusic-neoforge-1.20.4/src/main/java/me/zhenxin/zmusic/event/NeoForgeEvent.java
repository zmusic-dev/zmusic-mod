package me.zhenxin.zmusic.event;

import me.zhenxin.zmusic.ZMusic;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.TickEvent;

/**
 * NeoForge 事件
 *
 * @author 真心
 * @since 2026-04-24 11:00
 */
public class NeoForgeEvent {

    @SubscribeEvent
    public void onSound(final PlaySoundEvent event) {
        if (!ZMusic.getPlayer().isPlay() || event.getSound() == null) {
            return;
        }

        SoundSource data = event.getSound().getSource();
        switch (data) {
            case MUSIC:
            case RECORDS:
                event.setSound(null);
                break;
            default:
        }
    }

    @SubscribeEvent
    public void onServerQuit(final ClientPlayerNetworkEvent.LoggingOut event) {
        try {
            ZMusic.getPlayer().closePlayer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        ZMusic.getPlayer().tick();
    }
}
