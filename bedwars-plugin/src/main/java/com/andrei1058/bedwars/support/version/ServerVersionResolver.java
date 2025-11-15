/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2021 Andrei Dascălu
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Contact e-mail: andrew.dascalu@gmail.com
 */

package com.andrei1058.bedwars.support.version;

import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which {@code VersionSupport} implementation must be loaded.
 * <p>
 * Up to 1.20.4 the implementation was selected using the relocated CraftBukkit
 * package ({@code org.bukkit.craftbukkit.v1_20_R3}). Paper stopped relocating that
 * package in 1.20.5 and Minecraft 26.x ships unrelocated, so the third segment of the
 * server class name is no longer a version (it is simply {@code CraftServer}).
 * <p>
 * From 1.21 on the revision is derived from the Minecraft version instead, using {@link #REVISIONS}.
 * Every revision has its own {@code versionsupport_<revision>} module that extends the previous one,
 * exactly like the older modules do, and none of them touches NMS/CraftBukkit.
 * <p>
 * To support a new Minecraft release: add a module extending the newest one, then add one line to
 * {@link #REVISIONS}. Servers newer than the last entry use the newest module.
 */
public final class ServerVersionResolver {

    /**
     * Revision modules for 1.21 and newer, ordered from the oldest. Each entry is
     * {@code {firstMajor, firstMinor, firstPatch, revision}}: the revision applies from that Minecraft version
     * up to (excluding) the next entry.
     */
    private static final Object[][] REVISIONS = new Object[][]{
            {1, 21, 0, "v1_21_R1"},
            {1, 21, 2, "v1_21_R2"},
            {1, 21, 4, "v1_21_R3"},
            {1, 21, 5, "v1_21_R4"},
            {1, 21, 6, "v1_21_R5"},
            {1, 21, 9, "v1_21_R6"},
            {1, 21, 11, "v1_21_R7"},
            {26, 0, 0, "v26_R1"},
    };

    private static final Pattern CRAFT_REVISION = Pattern.compile("^v\\d+_\\d+_R\\d+$");
    private static final Pattern MC_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    private static final int[] MINECRAFT = parseMinecraftVersion(Bukkit.getBukkitVersion());
    private static final String CRAFT_SEGMENT = readCraftSegment();

    private ServerVersionResolver() {
    }

    /**
     * Parse a bukkit version string such as {@code 1.21.4-R0.1-SNAPSHOT} or
     * {@code 26.1.2-R0.1-SNAPSHOT}.
     *
     * @return {major, minor, patch}. Unknown parts are 0.
     */
    static int[] parseMinecraftVersion(String bukkitVersion) {
        int[] result = new int[]{0, 0, 0};
        if (bukkitVersion == null) {
            return result;
        }
        Matcher matcher = MC_VERSION.matcher(bukkitVersion.trim());
        if (!matcher.find()) {
            return result;
        }
        result[0] = Integer.parseInt(matcher.group(1));
        result[1] = Integer.parseInt(matcher.group(2));
        if (matcher.group(3) != null) {
            result[2] = Integer.parseInt(matcher.group(3));
        }
        return result;
    }

    private static String readCraftSegment() {
        String[] parts = Bukkit.getServer().getClass().getName().split("\\.");
        return parts.length > 3 ? parts[3] : "";
    }

    /**
     * @return true when the running server is 1.21 or newer (including 26.x).
     */
    public static boolean isModern() {
        return isModern(MINECRAFT);
    }

    static boolean isModern(int[] mc) {
        return mc[0] >= 26 || (mc[0] == 1 && mc[1] >= 21);
    }

    /**
     * Name handed to the version support constructor and exposed through
     * {@code BedWars#getServerVersion()}, for example {@code v1_20_R3} or {@code v1_21_R3}.
     */
    public static String getVersionName() {
        return getVersionName(MINECRAFT, CRAFT_SEGMENT);
    }

    static String getVersionName(int[] mc, String craftSegment) {
        if (CRAFT_REVISION.matcher(craftSegment).matches()) {
            return craftSegment;
        }
        if (isModern(mc)) {
            return revisionFor(mc);
        }
        StringBuilder name = new StringBuilder("v").append(mc[0]).append('_').append(mc[1]);
        if (mc[2] > 0) {
            name.append('_').append(mc[2]);
        }
        return name.toString();
    }

    /**
     * @return the newest revision whose first version is not above {@code mc}.
     */
    static String revisionFor(int[] mc) {
        String found = (String) REVISIONS[0][3];
        for (Object[] entry : REVISIONS) {
            int[] first = new int[]{(Integer) entry[0], (Integer) entry[1], (Integer) entry[2]};
            if (compare(mc, first) >= 0) {
                found = (String) entry[3];
            }
        }
        return found;
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }

    /**
     * @param versionName value returned by {@link #getVersionName()}
     * @return fully qualified name of the version support class to load.
     */
    public static String getSupportClassName(String versionName) {
        return "com.andrei1058.bedwars.support.version." + versionName + "." + versionName;
    }

    /**
     * @return fully qualified name of the sidebar provider used when the sidebar library has none for this server.
     * It lives in the first 1.21 revision and is inherited by every later one.
     */
    public static String getModernSidebarProviderName() {
        return "com.andrei1058.bedwars.support.version.v1_21_R1.sidebar.ModernSidebarProvider";
    }

    /**
     * Paper replaced {@code com.destroystokyo.paper.PaperConfig} in 1.19, so several markers are checked.
     */
    public static boolean detectPaper() {
        String[] markers = new String[]{
                "com.destroystokyo.paper.PaperConfig",
                "io.papermc.paper.configuration.Configuration",
                "io.papermc.paper.ServerBuildInfo",
                "io.papermc.paper.threadedregions.RegionizedServer"
        };
        for (String marker : markers) {
            try {
                Class.forName(marker);
                return true;
            } catch (ClassNotFoundException ignored) {
                // try next
            }
        }
        return false;
    }

    /**
     * @return true when running on Spigot or on one of its forks.
     */
    public static boolean isSpigotCompatible() {
        String[] markers = new String[]{
                "org.spigotmc.SpigotConfig",
                "org.spigotmc.SpigotWorldConfig",
                "org.bukkit.Server$Spigot"
        };
        for (String marker : markers) {
            try {
                Class.forName(marker);
                return true;
            } catch (ClassNotFoundException ignored) {
                // try next
            }
        }
        return detectPaper();
    }
}
