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

import java.util.Arrays;
import java.util.List;
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
     * Revision modules for 1.21 and newer, ordered from the oldest. Each revision applies from its first
     * Minecraft version up to (excluding) the first version of the next entry.
     */
    private static final List<Revision> REVISIONS = Arrays.asList(
            new Revision("v1_21_R1", 1, 21, 0),
            new Revision("v1_21_R2", 1, 21, 2),
            new Revision("v1_21_R3", 1, 21, 4),
            new Revision("v1_21_R4", 1, 21, 5),
            new Revision("v1_21_R5", 1, 21, 6),
            new Revision("v1_21_R6", 1, 21, 9),
            new Revision("v1_21_R7", 1, 21, 11),
            new Revision("v26_R1", 26, 0, 0)
    );

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
        int[] version = new int[]{0, 0, 0};
        if (bukkitVersion == null) {
            return version;
        }
        Matcher matcher = MC_VERSION.matcher(bukkitVersion.trim());
        if (!matcher.find()) {
            return version;
        }
        version[0] = Integer.parseInt(matcher.group(1));
        version[1] = Integer.parseInt(matcher.group(2));
        boolean hasPatchNumber = matcher.group(3) != null;
        if (hasPatchNumber) {
            version[2] = Integer.parseInt(matcher.group(3));
        }
        return version;
    }

    /**
     * On servers that relocate CraftBukkit the fourth part of the server class name is the revision
     * (org.bukkit.craftbukkit.v1_20_R3.CraftServer). Elsewhere it is not.
     */
    private static String readCraftSegment() {
        String[] packageParts = Bukkit.getServer().getClass().getName().split("\\.");
        boolean hasRevisionPart = packageParts.length > 3;
        return hasRevisionPart ? packageParts[3] : "";
    }

    /**
     * @return true when the running server is 1.21 or newer (including 26.x).
     */
    public static boolean isModern() {
        return isModern(MINECRAFT);
    }

    static boolean isModern(int[] minecraftVersion) {
        int major = minecraftVersion[0];
        int minor = minecraftVersion[1];
        boolean isNumberedLikeYearReleases = major >= 26;
        boolean isOneTwentyOneOrNewer = major == 1 && minor >= 21;
        return isNumberedLikeYearReleases || isOneTwentyOneOrNewer;
    }

    /**
     * Name handed to the version support constructor and exposed through
     * {@code BedWars#getServerVersion()}, for example {@code v1_20_R3} or {@code v1_21_R3}.
     */
    public static String getVersionName() {
        return getVersionName(MINECRAFT, CRAFT_SEGMENT);
    }

    static String getVersionName(int[] minecraftVersion, String craftSegment) {
        boolean serverReportsItsRevision = CRAFT_REVISION.matcher(craftSegment).matches();
        if (serverReportsItsRevision) {
            return craftSegment;
        }
        if (isModern(minecraftVersion)) {
            return revisionFor(minecraftVersion);
        }
        // Unsupported server without a revision, the name is only used in error messages.
        StringBuilder name = new StringBuilder("v").append(minecraftVersion[0]).append('_').append(minecraftVersion[1]);
        if (minecraftVersion[2] > 0) {
            name.append('_').append(minecraftVersion[2]);
        }
        return name.toString();
    }

    /**
     * @return the newest revision whose first version is not above {@code minecraftVersion}.
     */
    static String revisionFor(int[] minecraftVersion) {
        String matchingRevision = REVISIONS.get(0).name;
        for (Revision revision : REVISIONS) {
            boolean serverIsAtLeastThisRevision = compareVersions(minecraftVersion, revision.firstVersion) >= 0;
            if (serverIsAtLeastThisRevision) {
                matchingRevision = revision.name;
            }
        }
        return matchingRevision;
    }

    /**
     * Compares two {major, minor, patch} versions.
     *
     * @return negative if {@code left} is older, 0 if equal, positive if {@code left} is newer.
     */
    private static int compareVersions(int[] left, int[] right) {
        for (int part = 0; part < 3; part++) {
            if (left[part] != right[part]) {
                return Integer.compare(left[part], right[part]);
            }
        }
        return 0;
    }

    /**
     * A server revision module and the first Minecraft version it applies to.
     */
    private static final class Revision {

        private final String name;
        private final int[] firstVersion;

        private Revision(String name, int major, int minor, int patch) {
            this.name = name;
            this.firstVersion = new int[]{major, minor, patch};
        }
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
        return anyClassExists(
                "com.destroystokyo.paper.PaperConfig",
                "io.papermc.paper.configuration.Configuration",
                "io.papermc.paper.ServerBuildInfo",
                "io.papermc.paper.threadedregions.RegionizedServer"
        );
    }

    /**
     * @return true when running on Spigot or on one of its forks (Paper is one of them).
     */
    public static boolean isSpigotCompatible() {
        boolean isSpigot = anyClassExists(
                "org.spigotmc.SpigotConfig",
                "org.spigotmc.SpigotWorldConfig",
                "org.bukkit.Server$Spigot"
        );
        return isSpigot || detectPaper();
    }

    private static boolean anyClassExists(String... classNames) {
        for (String className : classNames) {
            try {
                Class.forName(className);
                return true;
            } catch (ClassNotFoundException notPresent) {
                // try the next marker
            }
        }
        return false;
    }
}
