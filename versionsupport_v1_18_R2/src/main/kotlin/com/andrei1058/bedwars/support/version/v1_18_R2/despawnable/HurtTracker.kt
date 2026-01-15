package com.andrei1058.bedwars.support.version.v1_18_R2.despawnable

import com.andrei1058.bedwars.api.server.VersionSupport
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers who hurt a team golem/silverfish so it can retaliate (vanilla HurtByTargetGoal).
 */
class HurtTracker(private val versionSupport: VersionSupport) : Listener {

    private val lastAttacker = ConcurrentHashMap<UUID, LivingEntity>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val victimId = event.entity.uniqueId
        val victimIsTeamMob = versionSupport.despawnablesList.containsKey(victimId)
        if (!victimIsTeamMob) return

        var attacker: Entity? = event.damager
        if (attacker is Projectile) {
            // blame whoever shot it, not the arrow
            attacker = attacker.shooter as? Entity
        }
        if (attacker is LivingEntity) {
            lastAttacker[victimId] = attacker
        }
    }

    /**
     * @return the last attacker, only once.
     */
    fun consume(victimId: UUID): LivingEntity? = lastAttacker.remove(victimId)

    fun forget(victimId: UUID) {
        lastAttacker.remove(victimId)
    }
}
