package com.andrei1058.bedwars.support.version.v1_18_R2.despawnable

import com.andrei1058.bedwars.api.arena.team.ITeam
import com.andrei1058.bedwars.api.server.VersionSupport
import com.andrei1058.bedwars.support.version.v1_18_R2.VersionCompat
import org.bukkit.GameMode
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import java.util.concurrent.ThreadLocalRandom

/**
 * Target selection of team golems and silverfish.
 *
 * It follows the vanilla `NearestAttackableTargetGoal` (random interval of 20 ticks, must see the target,
 * search box of follow range x 4 blocks vertically, range reduced for sneaking/invisible targets) filtered by the
 * same predicate the NMS based versions used:
 *  - players: alive, not a member of the team, not re-spawning, not spectating;
 *  - anything else: not a despawnable of the same team.
 */
class TeamTargeting(
    val mob: Mob,
    private val team: ITeam,
    private val versionSupport: VersionSupport
) {

    private val followRange = VersionCompat.attribute("follow_range")

    private var unseenTicks = 0

    private fun followDistance(): Double {
        val attribute = followRange ?: return DEFAULT_FOLLOW_DISTANCE
        return mob.getAttribute(attribute)?.value ?: DEFAULT_FOLLOW_DISTANCE
    }

    /**
     * The BedWars specific rule: who counts as an enemy of this team's mob.
     */
    internal fun isEnemy(entity: LivingEntity): Boolean {
        if (entity is Player) {
            val playerId = entity.uniqueId
            val arena = team.arena
            val isOutOfTheFight = arena.isReSpawning(playerId) || arena.isSpectator(playerId)
            return !entity.isDead && !team.wasMember(playerId) && !isOutOfTheFight
        }
        return !isMobOfSameTeam(entity)
    }

    private fun isMobOfSameTeam(entity: Entity): Boolean {
        val despawnable = versionSupport.despawnablesList[entity.uniqueId]
        return despawnable != null && despawnable.team === team
    }

    /**
     * Vanilla rules for what a mob may attack at all, regardless of teams.
     */
    private fun isAttackable(entity: LivingEntity): Boolean {
        if (entity == mob) return false
        if (!entity.isValid || entity.isDead) return false
        if (entity.isInvulnerable) return false
        if (entity is ArmorStand) return false
        if (entity is Player && (entity.gameMode == GameMode.CREATIVE || entity.gameMode == GameMode.SPECTATOR)) {
            return false
        }
        return entity.world == mob.world
    }

    /**
     * Mirrors the vanilla visibility factor: sneaking and invisible targets are noticed from a shorter distance.
     * Invisible targets are easier to notice the more armor they wear.
     */
    private fun visibilityFactor(target: LivingEntity): Double {
        var factor = 1.0
        // only players can sneak, and the API has no sneaking flag for other living entities in older versions
        if (target is Player && target.isSneaking) {
            factor *= 0.8
        }
        if (target.isInvisible) {
            val armorCover = maxOf(armorCoverage(target), 0.1)
            factor *= 0.7 * armorCover
        }
        return factor
    }

    /**
     * @return share of armor slots that are filled, from 0 (naked) to 1 (full set).
     */
    private fun armorCoverage(target: LivingEntity): Double {
        val equipment = target.equipment ?: return 0.0
        val armorSlots = equipment.armorContents
        val wornPieces = armorSlots.count { piece -> piece != null && !piece.type.isAir }
        return wornPieces / maxOf(1, armorSlots.size).toDouble()
    }

    private fun isWithinNoticeRange(target: LivingEntity): Boolean {
        val noticeDistance = maxOf(followDistance() * visibilityFactor(target), MIN_NOTICE_DISTANCE)
        return mob.location.distanceSquared(target.location) <= noticeDistance * noticeDistance
    }

    private fun isValidNewTarget(candidate: LivingEntity): Boolean {
        return isAttackable(candidate) && isEnemy(candidate)
                && isWithinNoticeRange(candidate) && mob.hasLineOfSight(candidate)
    }

    /**
     * Called every tick while the mob is alive. Keeps or drops the current target and looks for a new one.
     *
     * @param lastAttacker last living entity that damaged the mob (consumed by the caller), can be null.
     */
    fun update(lastAttacker: LivingEntity?) {
        val currentTarget = mob.target

        // Retaliating against whoever hurt the mob has the highest priority (vanilla HurtByTargetGoal).
        val shouldRetaliate = lastAttacker != null && lastAttacker != currentTarget
                && isAttackable(lastAttacker) && isEnemy(lastAttacker)
        if (shouldRetaliate) {
            changeTarget(lastAttacker)
            return
        }

        if (currentTarget != null) {
            keepOrDropTarget(currentTarget)
            return
        }

        searchForNewTarget()
    }

    /**
     * Vanilla TargetGoal#canContinueToUse: drop targets that became invalid, left the follow range or have
     * been out of sight for too long.
     */
    private fun keepOrDropTarget(target: LivingEntity) {
        if (!isAttackable(target) || !isEnemy(target)) {
            changeTarget(null)
            return
        }

        val followDistance = followDistance()
        val outOfRange = mob.location.distanceSquared(target.location) > followDistance * followDistance
        if (outOfRange) {
            changeTarget(null)
            return
        }

        if (mob.hasLineOfSight(target)) {
            unseenTicks = 0
        } else {
            unseenTicks++
            if (unseenTicks > MAX_UNSEEN_TICKS) {
                changeTarget(null)
            }
        }
    }

    /**
     * Vanilla NearestAttackableTargetGoal#canUse: on average once every [SEARCH_INTERVAL_TICKS] ticks
     * pick the closest valid enemy around.
     */
    private fun searchForNewTarget() {
        val searchThisTick = ThreadLocalRandom.current().nextInt(SEARCH_INTERVAL_TICKS) == 0
        if (!searchThisTick) {
            return
        }
        val nearestEnemy = findNearestEnemy()
        if (nearestEnemy != null) {
            changeTarget(nearestEnemy)
        }
    }

    private fun changeTarget(newTarget: LivingEntity?) {
        mob.target = newTarget
        unseenTicks = 0
    }

    private fun findNearestEnemy(): LivingEntity? {
        val searchRadius = followDistance()

        return mob.getNearbyEntities(searchRadius, SEARCH_HEIGHT, searchRadius)
            .filterIsInstance<LivingEntity>()
            .filter { candidate -> isValidNewTarget(candidate) }
            .minByOrNull { candidate -> mob.location.distanceSquared(candidate.location) }
    }

    private companion object {
        /** Average number of ticks between two searches for a target. */
        const val SEARCH_INTERVAL_TICKS = 20

        /** Ticks a target may stay out of sight before the mob gives up on it. */
        const val MAX_UNSEEN_TICKS = 60

        /** Vertical half-size of the search box, horizontally the follow range is used. */
        const val SEARCH_HEIGHT = 4.0
        const val MIN_NOTICE_DISTANCE = 2.0
        const val DEFAULT_FOLLOW_DISTANCE = 16.0
    }
}
