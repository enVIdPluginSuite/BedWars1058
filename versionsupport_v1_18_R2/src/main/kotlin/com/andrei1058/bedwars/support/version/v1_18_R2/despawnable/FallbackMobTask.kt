package com.andrei1058.bedwars.support.version.v1_18_R2.despawnable

import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable

/**
 * Used on servers without the Paper mob goal API (plain Spigot). Without NMS the vanilla goals cannot be removed,
 * so movement and attacks stay vanilla and only the target selection is driven here.
 */
class FallbackMobTask private constructor(
    private val targeting: TeamTargeting,
    private val hurtTracker: HurtTracker
) : BukkitRunnable() {

    override fun run() {
        val mob = targeting.mob
        if (!mob.isValid) {
            hurtTracker.forget(mob.uniqueId)
            cancel()
            return
        }
        targeting.update(hurtTracker.consume(mob.uniqueId))
    }

    companion object {
        @JvmStatic
        fun start(plugin: Plugin, targeting: TeamTargeting, hurtTracker: HurtTracker) {
            FallbackMobTask(targeting, hurtTracker).runTaskTimer(plugin, 1L, 1L)
        }
    }
}
