package com.andrei1058.bedwars.support.version.modern;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Changes the blast resistance of end stone and glass so they can be used as the (in)famous
 * "TNT protection" blocks, exactly what the NMS based modules did.
 * <p>
 * The blast resistance is a plain field of the internal block class, there is no API to change it.
 * To stay independent from the server revision nothing is referenced at compile time:
 * <ul>
 *     <li>the block instance is resolved with CraftMagicNumbers, whose package is read from the running server
 *     (relocated with a revision on Spigot, plain on Paper 1.20.5+ and 26.x);</li>
 *     <li>the field is looked up by its Mojang name and, if the server is obfuscated, by signature: the only float
 *     field whose value equals {@link Material#getBlastResistance()} for a set of well known blocks.</li>
 * </ul>
 */
final class BlastResistance {

    private static final String MOJANG_FIELD_NAME = "explosionResistance";

    private BlastResistance() {
    }

    static boolean apply(float endStoneBlast, float glassBlast) throws ReflectiveOperationException {
        Method getBlock = findGetBlock();

        List<Material> glass = new ArrayList<>();
        glass.add(Material.GLASS);
        glass.add(Material.TINTED_GLASS);
        for (Material material : Material.values()) {
            if (!material.isLegacy() && material.name().endsWith("_STAINED_GLASS")) {
                glass.add(material);
            }
        }

        Field field = findResistanceField(getBlock);
        if (field == null) {
            return false;
        }
        field.setAccessible(true);

        field.setFloat(getBlock.invoke(null, Material.END_STONE), endStoneBlast);
        for (Material material : glass) {
            field.setFloat(getBlock.invoke(null, material), glassBlast);
        }
        return true;
    }

    private static @NotNull Method findGetBlock() throws ClassNotFoundException, NoSuchMethodException {
        String craftPackage = Bukkit.getServer().getClass().getPackageName();
        Class<?> magicNumbers = Class.forName(craftPackage + ".util.CraftMagicNumbers");
        try {
            return magicNumbers.getMethod("getBlock", Material.class);
        } catch (NoSuchMethodException e) {
            for (Method method : magicNumbers.getMethods()) {
                if (method.getName().equals("getBlock") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(Material.class)
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    return method;
                }
            }
            throw e;
        }
    }

    private static Field findResistanceField(Method getBlock) throws ReflectiveOperationException {
        Object stone = getBlock.invoke(null, Material.STONE);
        // walk up: Block -> BlockBehaviour
        for (Class<?> c = stone.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field byName = c.getDeclaredField(MOJANG_FIELD_NAME);
                if (byName.getType() == float.class && !Modifier.isStatic(byName.getModifiers())) {
                    return byName;
                }
            } catch (NoSuchFieldException ignored) {
                // keep walking
            }
        }
        // obfuscated names: match by value
        Material[] calibrators = {Material.STONE, Material.OBSIDIAN, Material.END_STONE, Material.GLASS, Material.BEDROCK, Material.DIRT};
        for (Class<?> c = stone.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            candidates:
            for (Field candidate : c.getDeclaredFields()) {
                if (candidate.getType() != float.class || Modifier.isStatic(candidate.getModifiers())) continue;
                candidate.setAccessible(true);
                for (Material calibrator : calibrators) {
                    float value = candidate.getFloat(getBlock.invoke(null, calibrator));
                    if (Math.abs(value - calibrator.getBlastResistance()) > 0.0001f) {
                        continue candidates;
                    }
                }
                return candidate;
            }
        }
        return null;
    }
}
