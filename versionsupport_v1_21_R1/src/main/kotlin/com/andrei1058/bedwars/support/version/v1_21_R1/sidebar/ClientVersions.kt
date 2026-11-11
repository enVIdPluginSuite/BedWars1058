package com.andrei1058.bedwars.support.version.v1_21_R1.sidebar

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.lang.reflect.Method

/**
 * Tells whether a player's client is older than 1.13, reached through ViaVersion (Lunar 1.8.9, for example).
 * Such clients cannot show more than 16 characters per scoreboard team prefix or suffix and are unreliable with
 * anything beyond that, so the sidebar draws their rows differently. Without ViaVersion everybody is a modern client.
 */
internal object ClientVersions {
    /** Protocol number of 1.13, the first version with chat components in scoreboard teams. */
    private const val PROTOCOL_1_13 = 393

    private val lookup: Lookup? by lazy {
        try {
            if (Bukkit.getPluginManager().getPlugin("ViaVersion") == null) return@lazy null
            val via = Class.forName("com.viaversion.viaversion.api.Via")
            val api = via.getMethod("getAPI").invoke(null)
            val method = api.javaClass.getMethod("getPlayerVersion", java.util.UUID::class.java)
            method.isAccessible = true
            Lookup(api, method)
        } catch (_: Throwable) {
            null
        }
    }

    private class Lookup(val api: Any, val method: Method)

    fun isLegacy(player: Player): Boolean {
        val l = lookup ?: return false
        return try {
            val protocol = l.method.invoke(l.api, player.uniqueId) as Int
            protocol in 1 until PROTOCOL_1_13
        } catch (_: Throwable) {
            false
        }
    }
}
