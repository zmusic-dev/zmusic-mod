package me.zhenxin.zmusic.manager;

import lombok.extern.log4j.Log4j2;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 通过反射按方法签名（而非名称）定位 {@link Minecraft} 相关 API。
 *
 * <p>部分 Forge↔Fabric 桥接 mod（如 Sinytra Connector）会在加载阶段重写
 * {@link Minecraft} 类，导致编译期使用的官方方法名（如 {@code getInstance}）
 * 在运行时不存在，只剩下 SRG 名（如 {@code m_91087_}）。按参数/返回类型匹配
 * 而非按名称查找，可以兼容两种命名方案。</p>
 */
@Log4j2
final class MinecraftSoundAccess {

    private static volatile boolean resolved;
    private static Method getInstance;
    private static Field optionsField;
    private static Method getSoundSourceVolume;
    private static Method getSoundManager;
    private static Method stopSound;

    private MinecraftSoundAccess() {
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            for (Method m : Minecraft.class.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                        && m.getReturnType() == Minecraft.class) {
                    m.setAccessible(true);
                    getInstance = m;
                    break;
                }
            }
            for (Field f : Minecraft.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == Options.class) {
                    f.setAccessible(true);
                    optionsField = f;
                    break;
                }
            }
            for (Method m : Options.class.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == SoundSource.class
                        && m.getReturnType() == float.class) {
                    m.setAccessible(true);
                    getSoundSourceVolume = m;
                    break;
                }
            }
            for (Method m : Minecraft.class.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                        && m.getReturnType() == net.minecraft.client.sounds.SoundManager.class) {
                    m.setAccessible(true);
                    getSoundManager = m;
                    break;
                }
            }
            for (Method m : net.minecraft.client.sounds.SoundManager.class.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (!Modifier.isStatic(m.getModifiers()) && params.length == 2
                        && params[0] == ResourceLocation.class && params[1] == SoundSource.class) {
                    m.setAccessible(true);
                    stopSound = m;
                    break;
                }
            }
            if (getInstance == null || optionsField == null || getSoundSourceVolume == null
                    || getSoundManager == null || stopSound == null) {
                log.warn("ZMusic could not fully resolve Minecraft sound API via reflection " +
                        "(getInstance={}, options={}, getSoundSourceVolume={}, getSoundManager={}, stop={})",
                        getInstance, optionsField, getSoundSourceVolume, getSoundManager, stopSound);
            }
        } catch (Throwable t) {
            log.warn("ZMusic failed to resolve Minecraft sound API via reflection", t);
        }
    }

    static float getRecordsVolume() {
        resolve();
        if (getInstance == null || optionsField == null || getSoundSourceVolume == null) {
            return 1.0f;
        }
        try {
            Object minecraft = getInstance.invoke(null);
            Object options = optionsField.get(minecraft);
            return (float) getSoundSourceVolume.invoke(options, SoundSource.RECORDS);
        } catch (Throwable t) {
            log.warn("ZMusic failed to read records volume via reflection", t);
            return 1.0f;
        }
    }

    static void stopMusicAndRecords() {
        resolve();
        if (getInstance == null || getSoundManager == null || stopSound == null) {
            return;
        }
        try {
            Object minecraft = getInstance.invoke(null);
            Object soundManager = getSoundManager.invoke(minecraft);
            stopSound.invoke(soundManager, null, SoundSource.MUSIC);
            stopSound.invoke(soundManager, null, SoundSource.RECORDS);
        } catch (Throwable t) {
            log.warn("ZMusic failed to stop vanilla sounds via reflection", t);
        }
    }
}
