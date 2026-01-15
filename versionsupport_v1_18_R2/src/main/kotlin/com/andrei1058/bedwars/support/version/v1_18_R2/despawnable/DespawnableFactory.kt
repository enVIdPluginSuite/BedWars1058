package com.andrei1058.bedwars.support.version.v1_18_R2.despawnable

import com.andrei1058.bedwars.api.arena.team.ITeam
import com.andrei1058.bedwars.api.language.Language
import com.andrei1058.bedwars.api.language.Messages
import com.andrei1058.bedwars.api.server.VersionSupport
import com.andrei1058.bedwars.support.version.v1_18_R2.VersionCompat
import com.andrei1058.bedwars.support.version.v1_18_R2.paper.TeamMobGoal
import org.bukkit.Location
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import java.util.logging.Level

/**
 * Spawns team silverfish and iron golems and gives them their BedWars behaviour.
 */
class DespawnableFactory(
    private val versionSupport: VersionSupport,
    private val hurtTracker: HurtTracker
) {

    private val plugin = versionSupport.plugin
    private val paperGoalsAvailable = VersionCompat.isPaper()
    private var fallbackWarningShown = false

    fun spawn(attributes: DespawnableAttributes, location: Location, team: ITeam): LivingEntity {
        val isGolem = attributes.type == DespawnableType.IRON_GOLEM
        val entityType = if (isGolem) EntityType.IRON_GOLEM else EntityType.SILVERFISH

        val world = requireNotNull(location.world) { "Cannot spawn a team mob in an unloaded world" }
        val entity = world.spawnEntity(location, entityType) as LivingEntity
        applyDefaultSettings(entity, attributes, team)

        if (entity is Mob) {
            val meleeSpeed = if (isGolem) GOLEM_MELEE_SPEED else SILVERFISH_MELEE_SPEED
            val strollSpeed = if (isGolem) GOLEM_STROLL_SPEED else SILVERFISH_STROLL_SPEED
            installBehaviour(entity, team, meleeSpeed, strollSpeed)
        }
        return entity
    }

    /**
     * Makes the mob fight for its team. Paper replaces the whole vanilla AI; on other servers only the
     * target selection can be replaced, see [FallbackMobTask].
     */
    private fun installBehaviour(mob: Mob, team: ITeam, meleeSpeed: Double, strollSpeed: Double) {
        val targeting = TeamTargeting(mob, team, versionSupport)

        if (paperGoalsAvailable) {
            try {
                TeamMobGoal.install(plugin, mob, targeting, hurtTracker, meleeSpeed, strollSpeed)
                return
            } catch (paperApiProblem: Throwable) {
                plugin.logger.log(Level.WARNING, "Could not apply custom mob goals, using the fallback.", paperApiProblem)
            }
        }

        if (!fallbackWarningShown) {
            fallbackWarningShown = true
            plugin.logger.warning(
                "The Paper mob goal API is not available: team golems and silverfish " +
                        "keep the vanilla movement AI. Use Paper for the exact behaviour."
            )
        }
        FallbackMobTask.start(plugin, targeting, hurtTracker)
    }

    /**
     * Initial name shown above the mob. The despawn countdown and health bar placeholders are refreshed every
     * second by `Despawnable#tick`, this only makes sure the first second already looks right.
     */
    private fun displayName(attributes: DespawnableAttributes, team: ITeam): String {
        val language = Language.getDefaultLanguage()
        val nameTemplate = if (attributes.type == DespawnableType.IRON_GOLEM) {
            Messages.SHOP_UTILITY_NPC_IRON_GOLEM_NAME
        } else {
            Messages.SHOP_UTILITY_NPC_SILVERFISH_NAME
        }

        val healthSymbol = language.m(Messages.FORMATTING_DESPAWNABLE_UTILITY_NPC_HEALTH) + " "
        val fullHealthBar = healthSymbol.repeat(HEALTH_SYMBOLS_IN_NAME)

        return language.m(nameTemplate)
            .replace("{despawn}", attributes.despawnSeconds.toString())
            .replace("{health}", fullHealthBar)
            .replace("{TeamColor}", team.color.chat().toString())
    }

    private fun applyDefaultSettings(entity: LivingEntity, attributes: DespawnableAttributes, team: ITeam) {
        entity.removeWhenFarAway = false
        entity.isPersistent = true
        entity.isCustomNameVisible = true
        entity.customName = displayName(attributes, team)

        setBaseValue(entity, "max_health", attributes.health)
        setBaseValue(entity, "movement_speed", attributes.speed)
        setBaseValue(entity, "attack_damage", attributes.damage)
    }

    /**
     * Attribute constants were renamed in 1.21.3, so they are looked up by registry name.
     */
    private fun setBaseValue(entity: LivingEntity, attributeName: String, value: Double) {
        val attribute = VersionCompat.attribute(attributeName) ?: return
        entity.getAttribute(attribute)?.baseValue = value
    }

    private companion object {
        // Speeds the NMS goals used: the melee attack speed and the random stroll speed.
        const val GOLEM_MELEE_SPEED = 1.5
        const val GOLEM_STROLL_SPEED = 1.0
        const val SILVERFISH_MELEE_SPEED = 1.9
        const val SILVERFISH_STROLL_SPEED = 2.0

        const val HEALTH_SYMBOLS_IN_NAME = 10
    }
}
