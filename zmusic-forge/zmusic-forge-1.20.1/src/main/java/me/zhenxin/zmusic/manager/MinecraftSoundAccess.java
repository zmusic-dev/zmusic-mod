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
            getInstance = findStaticMethod(Minecraft.class, 0, Minecraft.class);
            optionsField = findInstanceField(Minecraft.class, Options.class);
            getSoundSourceVolume = findInstanceMethod(Options.class, float.class, SoundSource.class);
            getSoundManager = findInstanceMethod(Minecraft.class, net.minecraft.client.sounds.SoundManager.class);
            stopSound = findInstanceMethod(net.minecraft.client.sounds.SoundManager.class, void.class,
                    ResourceLocation.class, SoundSource.class);

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

    /**
     * 查找一个静态、无参数、返回值为 {@code returnType} 的方法。
     */
    private static Method findStaticMethod(Class<?> owner, int paramCount, Class<?> returnType) {
        for (Method m : owner.getDeclaredMethods()) {
            if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == paramCount
                    && m.getReturnType() == returnType) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    /**
     * 查找一个非静态、类型为 {@code fieldType} 的实例字段。
     */
    private static Field findInstanceField(Class<?> owner, Class<?> fieldType) {
        for (Field f : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == fieldType) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    /**
     * 查找一个非静态、参数类型与 {@code paramTypes} 完全匹配、返回值为 {@code returnType} 的方法。
     */
    private static Method findInstanceMethod(Class<?> owner, Class<?> returnType, Class<?>... paramTypes) {
        for (Method m : owner.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) && m.getReturnType() == returnType
                    && java.util.Arrays.equals(m.getParameterTypes(), paramTypes)) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
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
