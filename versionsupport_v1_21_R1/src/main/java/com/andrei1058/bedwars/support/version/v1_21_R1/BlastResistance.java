package com.andrei1058.bedwars.support.version.v1_21_R1;

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

    /**
     * Blocks whose resistance is known through the API, used to recognise the right field on obfuscated servers.
     */
    private static final Material[] CALIBRATION_BLOCKS = {
            Material.STONE, Material.OBSIDIAN, Material.END_STONE, Material.GLASS, Material.BEDROCK, Material.DIRT
    };

    private static final float COMPARISON_TOLERANCE = 0.0001f;

    private BlastResistance() {
    }

    /**
     * @return false when the resistance field could not be found on this server version.
     */
    static boolean apply(float endStoneResistance, float glassResistance) throws ReflectiveOperationException {
        Method getInternalBlock = findGetInternalBlock();

        Field resistanceField = findResistanceField(getInternalBlock);
        if (resistanceField == null) {
            return false;
        }
        resistanceField.setAccessible(true);

        resistanceField.setFloat(getInternalBlock.invoke(null, Material.END_STONE), endStoneResistance);
        for (Material glassType : listGlassMaterials()) {
            resistanceField.setFloat(getInternalBlock.invoke(null, glassType), glassResistance);
        }
        return true;
    }

    /**
     * Plain glass, tinted glass and every stained glass color (panes are not part of the protection).
     */
    private static List<Material> listGlassMaterials() {
        List<Material> glassTypes = new ArrayList<>();
        glassTypes.add(Material.GLASS);
        glassTypes.add(Material.TINTED_GLASS);
        for (Material material : Material.values()) {
            boolean isStainedGlass = !material.isLegacy() && material.name().endsWith("_STAINED_GLASS");
            if (isStainedGlass) {
                glassTypes.add(material);
            }
        }
        return glassTypes;
    }

    /**
     * Finds {@code CraftMagicNumbers.getBlock(Material)}, which turns an API material into the internal block.
     */
    private static @NotNull Method findGetInternalBlock() throws ClassNotFoundException, NoSuchMethodException {
        String craftBukkitPackage = Bukkit.getServer().getClass().getPackageName();
        Class<?> magicNumbers = Class.forName(craftBukkitPackage + ".util.CraftMagicNumbers");
        try {
            return magicNumbers.getMethod("getBlock", Material.class);
        } catch (NoSuchMethodException exactSignatureMissing) {
            // Some revisions declare a wider parameter type, accept any static single argument "getBlock".
            for (Method method : magicNumbers.getMethods()) {
                boolean isGetBlock = method.getName().equals("getBlock")
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(Material.class)
                        && Modifier.isStatic(method.getModifiers());
                if (isGetBlock) {
                    return method;
                }
            }
            throw exactSignatureMissing;
        }
    }

    /**
     * Looks the field up by its Mojang name first and, on obfuscated servers, by its values.
     */
    private static Field findResistanceField(Method getInternalBlock) throws ReflectiveOperationException {
        Object stoneBlock = getInternalBlock.invoke(null, Material.STONE);
        Field fieldByName = findFieldByName(stoneBlock.getClass());
        if (fieldByName != null) {
            return fieldByName;
        }
        return findFieldByCalibration(stoneBlock.getClass(), getInternalBlock);
    }

    private static Field findFieldByName(Class<?> blockClass) {
        // the field lives in a parent class (Block -> BlockBehaviour), so walk up
        for (Class<?> type = blockClass; type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(MOJANG_FIELD_NAME);
                if (isInstanceFloat(field)) {
                    return field;
                }
            } catch (NoSuchFieldException notInThisClass) {
                // keep walking up
            }
        }
        return null;
    }

    /**
     * Picks the only float field that holds, for every calibration block, the same value as the API reports.
     */
    private static Field findFieldByCalibration(Class<?> blockClass, Method getInternalBlock)
            throws ReflectiveOperationException {
        for (Class<?> type = blockClass; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field candidate : type.getDeclaredFields()) {
                if (!isInstanceFloat(candidate)) {
                    continue;
                }
                candidate.setAccessible(true);
                if (matchesApiResistance(candidate, getInternalBlock)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean matchesApiResistance(Field candidate, Method getInternalBlock)
            throws ReflectiveOperationException {
        for (Material block : CALIBRATION_BLOCKS) {
            float fieldValue = candidate.getFloat(getInternalBlock.invoke(null, block));
            if (Math.abs(fieldValue - block.getBlastResistance()) > COMPARISON_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInstanceFloat(Field field) {
        return field.getType() == float.class && !Modifier.isStatic(field.getModifiers());
    }
}
