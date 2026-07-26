package me.zhenxin.zmusic.event;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.ZMusicPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.SoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Forge 事件
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/3/17 11:17
 */
@Log4j2
public class ForgeEvent {
    @SubscribeEvent
    public void onServerJoin(final ClientPlayerNetworkEvent.LoggingIn e) {
        ClientEvent.onConnected();
    }

    private boolean tickSyncDisabled;
    private boolean soundEventDisabled;

    private static volatile boolean soundSourceResolved;
    private static Method getSoundSource;

    /**
     * 通过反射按方法签名（而非名称）定位 {@link SoundInstance#getSource()}。
     * 部分 Forge↔Fabric 桥接 mod 会重写相关类，导致编译期使用的官方方法名
     * 在运行时不存在（只剩 SRG 名），此处按返回类型匹配以兼容两种命名方案。
     */
    private static synchronized void resolveSoundSource() {
        if (soundSourceResolved) {
            return;
        }
        soundSourceResolved = true;
        try {
            for (Method m : SoundInstance.class.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                        && m.getReturnType() == SoundSource.class) {
                    m.setAccessible(true);
                    getSoundSource = m;
                    break;
                }
            }
            if (getSoundSource == null) {
                log.warn("ZMusic could not resolve SoundInstance.getSource() via reflection");
            }
        } catch (Throwable t) {
            log.warn("ZMusic failed to resolve SoundInstance.getSource() via reflection", t);
        }
    }

    @SubscribeEvent
    public void onSound(final SoundEvent.SoundSourceEvent e) {
        if (soundEventDisabled || ZMusic.getPlayer().getState() != ZMusicPlayer.STATE_PLAYING || e.getSound() == null) {
            return;
        }
        try {
            resolveSoundSource();
            if (getSoundSource == null) {
                soundEventDisabled = true;
                return;
            }
            SoundSource data = (SoundSource) getSoundSource.invoke(e.getSound());
            //noinspection EnhancedSwitchMigration
            switch (data) {
                case MUSIC:
                case RECORDS:
                    e.getChannel().stop();
                    break;
                default:
            }
        } catch (Throwable t) {
            soundEventDisabled = true;
            log.error("ZMusic failed to handle vanilla sound event, disabling further attempts. " +
                    "This is likely caused by another mod conflicting with Minecraft's class structure.", t);
        }
    }

    @SubscribeEvent
    public void onServerQuit(final ClientPlayerNetworkEvent.LoggingOut e) {
        try {
            ClientEvent.onDisconnect();
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
