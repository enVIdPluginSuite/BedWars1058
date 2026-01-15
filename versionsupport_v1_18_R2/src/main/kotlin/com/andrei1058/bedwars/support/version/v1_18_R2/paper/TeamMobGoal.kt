package com.andrei1058.bedwars.support.version.v1_18_R2.paper

import com.andrei1058.bedwars.support.version.v1_18_R2.despawnable.HurtTracker
import com.andrei1058.bedwars.support.version.v1_18_R2.despawnable.TeamTargeting
import com.destroystokyo.paper.entity.ai.Goal
import com.destroystokyo.paper.entity.ai.GoalKey
import com.destroystokyo.paper.entity.ai.GoalType
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.plugin.Plugin
import java.util.EnumSet
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

/**
 * Behaviour of team golems and silverfish on Paper.
 *
 * The NMS versions replaced every vanilla goal with: float (1), melee attack (2), random stroll (3),
 * random look around (4), hurt-by target (1) and nearest attackable target (2). This single goal does the same
 * work each tick through the Paper API, so nothing depends on the server internals.
 */
class TeamMobGoal(
    plugin: Plugin,
    private val mob: Mob,
    private val targeting: TeamTargeting,
    private val hurtTracker: HurtTracker,
    private val meleeSpeed: Double,
    private val strollSpeed: Double
) : Goal<Mob> {

    private val goalKey: GoalKey<Mob> = GoalKey.of(Mob::class.java, NamespacedKey(plugin, "team_despawnable"))

    private var attackCooldownTicks = 0
    private var ticksUntilPathRecalculation = 0

    override fun shouldActivate(): Boolean = mob.isValid

    override fun shouldStayActive(): Boolean = mob.isValid

    override fun tick() {
        if (!mob.isValid) return

        // Same order as the vanilla goal priorities: stay afloat, pick a target, then fight or idle.
        floatInLiquid()

        targeting.update(hurtTracker.consume(mob.uniqueId))

        val target = mob.target
        if (target != null && target.isValid) {
            melee(target)
        } else {
            attackCooldownTicks = 0
            ticksUntilPathRecalculation = 0
            idle()
        }
    }

    override fun stop() {
        mob.pathfinder.stopPathfinding()
    }

    /**
     * FloatGoal: keep afloat.
     */
    private fun floatInLiquid() {
        val inLiquid = mob.isInWater || mob.isInLava
        val jumpThisTick = ThreadLocalRandom.current().nextFloat() < 0.8f
        if (inLiquid && jumpThisTick) {
            val velocity = mob.velocity
            mob.velocity = velocity.setY(velocity.y + 0.04)
        }
    }

    /**
     * MeleeAttackGoal: chase the target and hit it when close enough, at most once per cooldown.
     */
    private fun melee(target: LivingEntity) {
        mob.lookAt(target, 30f, 30f)

        updatePathTowards(target)

        if (attackCooldownTicks > 0) {
            attackCooldownTicks--
        }
        if (attackCooldownTicks <= 0 && isInHittingRange(target) && mob.hasLineOfSight(target)) {
            attackCooldownTicks = ATTACK_COOLDOWN_TICKS
            mob.swingMainHand()
            mob.attack(target)
        }
    }

    /**
     * Recalculating the path is expensive, so it is done every few ticks, less often for far away targets and
     * after a failed attempt (same timings as the vanilla melee goal).
     */
    private fun updatePathTowards(target: LivingEntity) {
        val recalculateNow = ticksUntilPathRecalculation <= 0
        ticksUntilPathRecalculation--
        if (!recalculateNow) {
            return
        }
        if (!mob.hasLineOfSight(target)) {
            ticksUntilPathRecalculation = 0
            return
        }

        ticksUntilPathRecalculation = 4 + ThreadLocalRandom.current().nextInt(7)
        val distanceSquared = mob.location.distanceSquared(target.location)
        if (distanceSquared > 1024.0) {
            ticksUntilPathRecalculation += 10
        } else if (distanceSquared > 256.0) {
            ticksUntilPathRecalculation += 5
        }

        val pathFound = mob.pathfinder.moveTo(target, meleeSpeed)
        if (!pathFound) {
            ticksUntilPathRecalculation += 15
        }
    }

    private fun isInHittingRange(target: LivingEntity): Boolean {
        val mobReach = mob.width * 2.0
        val hittingRangeSquared = mobReach * mobReach + target.width
        return mob.location.distanceSquared(target.location) <= hittingRangeSquared
    }

    /**
     * RandomStrollGoal and RandomLookAroundGoal.
     */
    private fun idle() {
        val random = ThreadLocalRandom.current()
        val pathfinder = mob.pathfinder

        val startStroll = !pathfinder.hasPath() && random.nextInt(STROLL_CHANCE_ONE_IN_TICKS) == 0
        if (startStroll) {
            // walk to a random spot up to 10 blocks away horizontally and 7 vertically
            val destination = mob.location.add(
                random.nextInt(-10, 11).toDouble(),
                random.nextInt(-7, 8).toDouble(),
                random.nextInt(-10, 11).toDouble()
            )
            pathfinder.moveTo(destination, strollSpeed)
        } else if (random.nextFloat() < LOOK_AROUND_CHANCE_PER_TICK) {
            // look at a random point on a circle around the eyes
            val angle = random.nextDouble() * Math.PI * 2.0
            val eyes = mob.eyeLocation
            mob.lookAt(eyes.x + cos(angle), eyes.y, eyes.z + sin(angle))
        }
    }

    override fun getKey(): GoalKey<Mob> = goalKey

    override fun getTypes(): EnumSet<GoalType> =
        EnumSet.of(GoalType.MOVE, GoalType.LOOK, GoalType.JUMP, GoalType.TARGET)

    companion object {
        private const val ATTACK_COOLDOWN_TICKS = 20
        private const val STROLL_CHANCE_ONE_IN_TICKS = 120
        private const val LOOK_AROUND_CHANCE_PER_TICK = 0.02f

        /**
         * Replaces the vanilla behaviour of the given mob.
         */
        @JvmStatic
        fun install(
            plugin: Plugin, mob: Mob, targeting: TeamTargeting, hurtTracker: HurtTracker,
            meleeSpeed: Double, strollSpeed: Double
        ) {
            val goals = Bukkit.getMobGoals()
            goals.removeAllGoals(mob)
            goals.addGoal(mob, 1, TeamMobGoal(plugin, mob, targeting, hurtTracker, meleeSpeed, strollSpeed))
        }
    }
}
