package com.andrei1058.bedwars.maprestore.internal;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * World storage differs between server versions.
 * <ul>
 *   <li>Up to 1.21.x a world is a folder in the server root: {@code <root>/<name>/region}.</li>
 *   <li>From 26.1 Paper stores every world, including the ones plugins create, inside the main level folder:
 *   {@code <level>/dimensions/minecraft/<name lower case>/region}. A map copied into the server root is ignored and
 *   the world is generated from scratch.</li>
 * </ul>
 * Maps are kept as plain folders / zips in the old layout; they are copied to {@link #worldDir(String)} to be played.
 */
public final class WorldLayout {

    private static final String[] DATA = {"region", "entities", "poi"};
    private static final String[] JUNK = {"dimensions", "DIM-1", "DIM1", "playerdata", "players", "stats", "advancements",
            "level.dat", "level.dat_old", "level.dat_mcr", "uid.dat", "session.lock"};

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

    /** Where the files of the world called {@code name} live while it is loaded. */
    public static File worldDir(String name) {
        if (!isModernServer()) return new File(Bukkit.getWorldContainer(), name);
        return new File(levelDirectory(), "dimensions/minecraft/" + name.toLowerCase(Locale.ENGLISH).replace(' ', '_'));
    }

    /** The folder of the main level ("world"). */
    public static File levelDirectory() {
        try {
            Object path = Bukkit.getServer().getClass().getMethod("getLevelDirectory").invoke(Bukkit.getServer());
            File f = ((Path) path).toFile();
            return f.isAbsolute() ? f : new File(Bukkit.getWorldContainer(), f.getPath());
        } catch (Throwable t) {
            return new File(Bukkit.getWorldContainer(), Bukkit.getWorlds().get(0).getName());
        }
    }

    /**
     * Makes a freshly copied map usable.
     * On 26.1+ {@code dir} is the final dimension folder: data is moved up from {@code dimensions/minecraft/overworld}
     * when the map was saved in the new layout, and files that belong to a whole level are dropped.
     * On older servers a map saved in the new layout is converted back to the old one.
     */
    public static void normalize(File dir) {
        if (!dir.isDirectory()) return;
        try {
            File overworld = new File(dir, "dimensions/minecraft/overworld");
            if (isModernServer()) {
                if (!new File(dir, "region").isDirectory() && new File(overworld, "region").isDirectory()) {
                    for (String n : DATA) move(new File(overworld, n), new File(dir, n));
                }
                for (String n : JUNK) {
                    File f = new File(dir, n);
                    if (f.isDirectory()) FileUtils.deleteDirectory(f);
                    else f.delete();
                }
            } else if (!new File(dir, "region").isDirectory() && new File(overworld, "region").isDirectory()) {
                for (String n : DATA) move(new File(overworld, n), new File(dir, n));
                File nether = new File(dir, "dimensions/minecraft/the_nether");
                if (nether.isDirectory() && !new File(dir, "DIM-1").exists()) move(nether, new File(dir, "DIM-1"));
                File end = new File(dir, "dimensions/minecraft/the_end");
                if (end.isDirectory() && !new File(dir, "DIM1").exists()) move(end, new File(dir, "DIM1"));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 26.1+: copies the played world back into {@code <root>/<name>} (old layout) so it can be zipped as the map.
     * Used when a setup session ends.
     */
    public static void exportToRoot(String name) throws IOException {
        if (!isModernServer()) return;
        File from = worldDir(name);
        File to = new File(Bukkit.getWorldContainer(), name);
        if (!new File(from, "region").isDirectory()) return;
        FileUtils.deleteDirectory(to);
        FileUtils.copyDirectory(from, to);
    }

    private static void move(File from, File to) throws IOException {
        if (!from.exists() || to.exists()) return;
        Files.move(from.toPath(), to.toPath());
    }
}
