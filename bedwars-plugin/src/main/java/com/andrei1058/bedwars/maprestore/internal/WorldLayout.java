package com.andrei1058.bedwars.maprestore.internal;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Minecraft 26.1 moved the overworld from the world root into dimensions/minecraft/overworld
 * (and DIM-1 / DIM1 into dimensions/minecraft/the_nether / the_end). A map saved in the other layout
 * would be loaded as a freshly generated vanilla world, so it is converted before the world is created.
 */
public final class WorldLayout {

    private static final String[] DATA = {"region", "entities", "poi"};
    private static final String[][] EXTRA = {{"DIM-1", "the_nether"}, {"DIM1", "the_end"}};

    private WorldLayout() {
    }

    /**
     * @return true on Minecraft 26.1+ ("26.3-R0.1-SNAPSHOT"; older versions start with "1.").
     */
    public static boolean isModernServer() {
        String v = Bukkit.getBukkitVersion();
        int end = 0;
        while (end < v.length() && Character.isDigit(v.charAt(end))) end++;
        if (end == 0) return false;
        try {
            return Integer.parseInt(v.substring(0, end)) >= 26;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void normalize(File world) {
        if (!world.isDirectory()) return;
        boolean modern = isModernServer();
        File overworld = new File(world, "dimensions/minecraft/overworld");
        try {
            if (modern) {
                if (new File(world, "region").isDirectory() && !new File(overworld, "region").isDirectory()) {
                    overworld.mkdirs();
                    for (String n : DATA) move(new File(world, n), new File(overworld, n));
                }
                for (String[] e : EXTRA) {
                    File target = new File(world, "dimensions/minecraft/" + e[1]);
                    if (new File(world, e[0]).isDirectory() && !new File(target, "region").isDirectory()) {
                        target.getParentFile().mkdirs();
                        move(new File(world, e[0]), target);
                    }
                }
            } else {
                if (new File(overworld, "region").isDirectory() && !new File(world, "region").isDirectory()) {
                    for (String n : DATA) move(new File(overworld, n), new File(world, n));
                }
                for (String[] e : EXTRA) {
                    File source = new File(world, "dimensions/minecraft/" + e[1]);
                    if (source.isDirectory() && !new File(world, e[0]).exists()) move(source, new File(world, e[0]));
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static void move(File from, File to) throws IOException {
        if (!from.exists() || to.exists()) return;
        Files.move(from.toPath(), to.toPath());
    }
}
