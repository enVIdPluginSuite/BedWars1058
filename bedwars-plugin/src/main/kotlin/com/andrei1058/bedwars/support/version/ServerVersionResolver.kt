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

package com.andrei1058.bedwars.support.version

import org.bukkit.Bukkit

/**
 * Works out which `VersionSupport` implementation must be loaded.
 *
 * Up to 1.20.4 the implementation was selected using the relocated CraftBukkit
 * package (`org.bukkit.craftbukkit.v1_20_R3`). Paper stopped relocating that
 * package in 1.20.5 and Minecraft 26.x ships unrelocated, so the third segment of the
 * server class name is no longer a version (it is simply `CraftServer`).
 *
 * From 1.20.5 on the revision is derived from the Minecraft version instead, using [revisions].
 * Every revision has its own `versionsupport_<revision>` module that extends the previous one,
 * exactly like the older modules do, and none of them touches NMS/CraftBukkit.
 *
 * To support a new Minecraft release: add a module extending the newest one, then add one line to
 * [revisions]. Servers newer than the last entry use the newest module.
 */
object ServerVersionResolver {

    /**
     * A server revision module and the first Minecraft version it applies to.
     */
    private class Revision(val name: String, major: Int, minor: Int, patch: Int) {
        val firstVersion = intArrayOf(major, minor, patch)
    }

    /**
     * Revision modules for 1.20.5 and newer, ordered from the oldest. Each revision applies from its first
     * Minecraft version up to (excluding) the first version of the next entry.
     */
    private val revisions = listOf(
        Revision("v1_20_R4", 1, 20, 5),
        Revision("v1_21_R1", 1, 21, 0),
        Revision("v1_21_R2", 1, 21, 2),
        Revision("v1_21_R3", 1, 21, 4),
        Revision("v1_21_R4", 1, 21, 5),
        Revision("v1_21_R5", 1, 21, 6),
        Revision("v1_21_R6", 1, 21, 9),
        Revision("v1_21_R7", 1, 21, 11),
        Revision("v26_R1", 26, 0, 0)
    )

    private val craftRevisionPattern = Regex("^v\\d+_\\d+_R\\d+$")
    private val minecraftVersionPattern = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?")

    private val minecraftVersion: IntArray = parseMinecraftVersion(Bukkit.getBukkitVersion())
    private val craftSegment: String = readCraftSegment()

    /**
     * Parse a bukkit version string such as `1.21.4-R0.1-SNAPSHOT` or `26.1.2-R0.1-SNAPSHOT`.
     *
     * @return {major, minor, patch}. Unknown parts are 0.
     */
    internal fun parseMinecraftVersion(bukkitVersion: String?): IntArray {
        val version = intArrayOf(0, 0, 0)
        val match = bukkitVersion?.trim()?.let { minecraftVersionPattern.find(it) } ?: return version

        version[0] = match.groupValues[1].toInt()
        version[1] = match.groupValues[2].toInt()
        val patchNumber = match.groups[3]?.value
        if (patchNumber != null) {
            version[2] = patchNumber.toInt()
        }
        return version
    }

    /**
     * On servers that relocate CraftBukkit the fourth part of the server class name is the revision
     * (org.bukkit.craftbukkit.v1_20_R3.CraftServer). Elsewhere it is not.
     */
    private fun readCraftSegment(): String {
        val packageParts = Bukkit.getServer().javaClass.name.split(".")
        val hasRevisionPart = packageParts.size > 3
        return if (hasRevisionPart) packageParts[3] else ""
    }

    /**
     * @return true when the running server is 1.20.5 or newer (including 26.x), the versions that do not relocate
     * CraftBukkit on Paper and are therefore picked by [revisions].
     */
    @JvmStatic
    fun isModern(): Boolean = isModern(minecraftVersion)

    internal fun isModern(minecraftVersion: IntArray): Boolean {
        val major = minecraftVersion[0]
        val minor = minecraftVersion[1]
        val isNumberedLikeYearReleases = major >= 26
        val patch = minecraftVersion[2]
        val isOneTwentyDotFiveOrNewer = major == 1 && (minor >= 21 || (minor == 20 && patch >= 5))
        return isNumberedLikeYearReleases || isOneTwentyDotFiveOrNewer
    }

    /**
     * Name handed to the version support constructor and exposed through
     * `BedWars#getServerVersion()`, for example `v1_20_R3` or `v1_21_R3`.
     */
    @JvmStatic
    fun getVersionName(): String = getVersionName(minecraftVersion, craftSegment)

    internal fun getVersionName(minecraftVersion: IntArray, craftSegment: String): String {
        val serverReportsItsRevision = craftRevisionPattern.matches(craftSegment)
        if (serverReportsItsRevision) {
            return craftSegment
        }
        if (isModern(minecraftVersion)) {
            return revisionFor(minecraftVersion)
        }
        // Unsupported server without a revision, the name is only used in error messages.
        val name = StringBuilder("v").append(minecraftVersion[0]).append('_').append(minecraftVersion[1])
        if (minecraftVersion[2] > 0) {
            name.append('_').append(minecraftVersion[2])
        }
        return name.toString()
    }

    /**
     * @return the newest revision whose first version is not above `minecraftVersion`.
     */
    internal fun revisionFor(minecraftVersion: IntArray): String {
        var matchingRevision = revisions.first().name
        for (revision in revisions) {
            val serverIsAtLeastThisRevision = compareVersions(minecraftVersion, revision.firstVersion) >= 0
            if (serverIsAtLeastThisRevision) {
                matchingRevision = revision.name
            }
        }
        return matchingRevision
    }

    /**
     * Compares two {major, minor, patch} versions.
     *
     * @return negative if `left` is older, 0 if equal, positive if `left` is newer.
     */
    private fun compareVersions(left: IntArray, right: IntArray): Int {
        for (part in 0 until 3) {
            if (left[part] != right[part]) {
                return left[part].compareTo(right[part])
            }
        }
        return 0
    }

    /**
     * @param versionName value returned by [getVersionName]
     * @return fully qualified name of the version support class to load.
     */
    @JvmStatic
    fun getSupportClassName(versionName: String): String =
        "com.andrei1058.bedwars.support.version.$versionName.$versionName"

    /**
     * @return fully qualified name of the sidebar provider used when the sidebar library has none for this server.
     * It lives in the first 1.21 revision and is inherited by every later one.
     */
    @JvmStatic
    fun getModernSidebarProviderName(): String =
        "com.andrei1058.bedwars.support.version.v1_21_R1.sidebar.ModernSidebarProvider"

    /**
     * Paper replaced `com.destroystokyo.paper.PaperConfig` in 1.19, so several markers are checked.
     */
    @JvmStatic
    fun detectPaper(): Boolean = anyClassExists(
        "com.destroystokyo.paper.PaperConfig",
        "io.papermc.paper.configuration.Configuration",
        "io.papermc.paper.ServerBuildInfo",
        "io.papermc.paper.threadedregions.RegionizedServer"
    )

    /**
     * @return true when running on Spigot or on one of its forks (Paper is one of them).
     */
    @JvmStatic
    fun isSpigotCompatible(): Boolean {
        val isSpigot = anyClassExists(
            "org.spigotmc.SpigotConfig",
            "org.spigotmc.SpigotWorldConfig",
            "org.bukkit.Server\$Spigot"
        )
        return isSpigot || detectPaper()
    }

    private fun anyClassExists(vararg classNames: String): Boolean {
        return classNames.any { className ->
            try {
                Class.forName(className)
                true
            } catch (notPresent: ClassNotFoundException) {
                false
            }
        }
    }
}
