package com.andrei1058.bedwars.support.version.v1_21_R1;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Small helpers hiding API differences between 1.21.x and 26.x, so the rest of the
 * module can be compiled once against the oldest supported API.
 */
public final class ModernCompat {

    private static final Method SOUND_VALUE_OF = findSoundValueOf();
    private static CommandMap commandMap;

    private ModernCompat() {
    }

    private static Method findSoundValueOf() {
        try {
            // Sound is an enum up to 1.21.1 and an interface (with a static valueOf) afterwards.
            return Sound.class.getMethod("valueOf", String.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Same contract as the old {@code Sound.valueOf}: throws if the sound does not exist.
     */
    public static @NotNull Sound sound(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Sound name is null");
        }
        if (SOUND_VALUE_OF != null) {
            try {
                Object sound = SOUND_VALUE_OF.invoke(null, name);
                if (sound != null) {
                    return (Sound) sound;
                }
            } catch (java.lang.reflect.InvocationTargetException e) {
                // fall through to the registry lookup, ex: the name is already a namespaced key
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Cannot resolve sound " + name, e);
            }
        }
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(java.util.Locale.ROOT));
        Sound sound = key == null ? null : Registry.SOUNDS.get(key);
        if (sound == null) {
            throw new IllegalArgumentException("No sound named " + name);
        }
        return sound;
    }

    /**
     * Attributes were renamed in 1.21.3 (generic.max_health became max_health) and
     * the constants changed with them. Registry keys are the stable thing.
     */
    public static @Nullable Attribute attribute(@NotNull String name) {
        Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(name));
        if (attribute == null) {
            attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + name));
        }
        return attribute;
    }

    public static @Nullable CommandMap commandMap() {
        if (commandMap == null) {
            try {
                Method m = Bukkit.getServer().getClass().getMethod("getCommandMap");
                commandMap = (CommandMap) m.invoke(Bukkit.getServer());
            } catch (ReflectiveOperationException e) {
                return null;
            }
        }
        return commandMap;
    }

    public static boolean isPaper() {
        try {
            Class.forName("com.destroystokyo.paper.entity.ai.MobGoals");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
