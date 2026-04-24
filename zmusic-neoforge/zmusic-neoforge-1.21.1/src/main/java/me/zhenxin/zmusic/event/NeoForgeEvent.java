package me.zhenxin.zmusic.event;

import me.zhenxin.zmusic.ZMusic;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * NeoForge 事件
 *
 * @author 真心
 * @since 2026-04-24 18:00
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
    public void onTick(ClientTickEvent.Post event) {
        ZMusic.getPlayer().tick();
    }
}
