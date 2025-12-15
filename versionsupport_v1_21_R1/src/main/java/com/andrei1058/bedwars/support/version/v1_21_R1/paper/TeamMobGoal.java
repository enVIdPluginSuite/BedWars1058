package com.andrei1058.bedwars.support.version.v1_21_R1.paper;

import com.andrei1058.bedwars.support.version.v1_21_R1.despawnable.HurtTracker;
import com.andrei1058.bedwars.support.version.v1_21_R1.despawnable.TeamTargeting;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Behaviour of team golems and silverfish on Paper.
 * <p>
 * The NMS versions replaced every vanilla goal with: float (1), melee attack (2), random stroll (3),
 * random look around (4), hurt-by target (1) and nearest attackable target (2). This single goal does the same
 * work each tick through the Paper API, so nothing depends on the server internals.
 */
public final class TeamMobGoal implements Goal<Mob> {

    private static final int ATTACK_COOLDOWN_TICKS = 20;
    private static final int STROLL_CHANCE_ONE_IN_TICKS = 120;
    private static final float LOOK_AROUND_CHANCE_PER_TICK = 0.02f;

    private final Mob mob;
    private final TeamTargeting targeting;
    private final HurtTracker hurtTracker;
    private final double meleeSpeed;
    private final double strollSpeed;
    private final GoalKey<Mob> key;

    private int attackCooldownTicks = 0;
    private int ticksUntilPathRecalculation = 0;

    public TeamMobGoal(@NotNull Plugin plugin, @NotNull Mob mob, @NotNull TeamTargeting targeting,
                       @NotNull HurtTracker hurtTracker, double meleeSpeed, double strollSpeed) {
        this.mob = mob;
        this.targeting = targeting;
        this.hurtTracker = hurtTracker;
        this.meleeSpeed = meleeSpeed;
        this.strollSpeed = strollSpeed;
        this.key = GoalKey.of(Mob.class, new NamespacedKey(plugin, "team_despawnable"));
    }

    /**
     * Replaces the vanilla behaviour of the given mob.
     */
    public static void install(@NotNull Plugin plugin, @NotNull Mob mob, @NotNull TeamTargeting targeting,
                               @NotNull HurtTracker hurtTracker, double meleeSpeed, double strollSpeed) {
        var goals = Bukkit.getMobGoals();
        goals.removeAllGoals(mob);
        goals.addGoal(mob, 1, new TeamMobGoal(plugin, mob, targeting, hurtTracker, meleeSpeed, strollSpeed));
    }

    @Override
    public boolean shouldActivate() {
        return mob.isValid();
    }

    @Override
    public boolean shouldStayActive() {
        return mob.isValid();
    }

    @Override
    public void tick() {
        if (!mob.isValid()) return;

        // Same order as the vanilla goal priorities: stay afloat, pick a target, then fight or idle.
        floatInLiquid();

        targeting.update(hurtTracker.consume(mob.getUniqueId()));

        LivingEntity target = mob.getTarget();
        if (target != null && target.isValid()) {
            melee(target);
        } else {
            attackCooldownTicks = 0;
            ticksUntilPathRecalculation = 0;
            idle();
        }
    }

    @Override
    public void stop() {
        mob.getPathfinder().stopPathfinding();
    }

    /**
     * FloatGoal: keep afloat.
     */
    private void floatInLiquid() {
        boolean inLiquid = mob.isInWater() || mob.isInLava();
        boolean jumpThisTick = ThreadLocalRandom.current().nextFloat() < 0.8f;
        if (inLiquid && jumpThisTick) {
            Vector velocity = mob.getVelocity();
            mob.setVelocity(velocity.setY(velocity.getY() + 0.04d));
        }
    }

    /**
     * MeleeAttackGoal: chase the target and hit it when close enough, at most once per cooldown.
     */
    private void melee(@NotNull LivingEntity target) {
        mob.lookAt(target, 30f, 30f);

        updatePathTowards(target);

        if (attackCooldownTicks > 0) {
            attackCooldownTicks--;
        }
        if (attackCooldownTicks <= 0 && isInHittingRange(target) && mob.hasLineOfSight(target)) {
            attackCooldownTicks = ATTACK_COOLDOWN_TICKS;
            mob.swingMainHand();
            mob.attack(target);
        }
    }

    /**
     * Recalculating the path is expensive, so it is done every few ticks, less often for far away targets and
     * after a failed attempt (same timings as the vanilla melee goal).
     */
    private void updatePathTowards(@NotNull LivingEntity target) {
        boolean recalculateNow = ticksUntilPathRecalculation <= 0;
        ticksUntilPathRecalculation--;
        if (!recalculateNow) {
            return;
        }
        if (!mob.hasLineOfSight(target)) {
            ticksUntilPathRecalculation = 0;
            return;
        }

        ticksUntilPathRecalculation = 4 + ThreadLocalRandom.current().nextInt(7);
        double distanceSquared = mob.getLocation().distanceSquared(target.getLocation());
        if (distanceSquared > 1024d) {
            ticksUntilPathRecalculation += 10;
        } else if (distanceSquared > 256d) {
            ticksUntilPathRecalculation += 5;
        }

        boolean pathFound = mob.getPathfinder().moveTo(target, meleeSpeed);
        if (!pathFound) {
            ticksUntilPathRecalculation += 15;
        }
    }

    private boolean isInHittingRange(@NotNull LivingEntity target) {
        double mobReach = mob.getWidth() * 2.0d;
        double hittingRangeSquared = mobReach * mobReach + target.getWidth();
        return mob.getLocation().distanceSquared(target.getLocation()) <= hittingRangeSquared;
    }

    /**
     * RandomStrollGoal and RandomLookAroundGoal.
     */
    private void idle() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Pathfinder pathfinder = mob.getPathfinder();

        boolean startStroll = !pathfinder.hasPath() && random.nextInt(STROLL_CHANCE_ONE_IN_TICKS) == 0;
        if (startStroll) {
            // walk to a random spot up to 10 blocks away horizontally and 7 vertically
            Location destination = mob.getLocation().add(
                    random.nextInt(-10, 11),
                    random.nextInt(-7, 8),
                    random.nextInt(-10, 11)
            );
            pathfinder.moveTo(destination, strollSpeed);
        } else if (random.nextFloat() < LOOK_AROUND_CHANCE_PER_TICK) {
            // look at a random point on a circle around the eyes
            double angle = random.nextDouble() * Math.PI * 2d;
            Location eyes = mob.getEyeLocation();
            mob.lookAt(eyes.getX() + Math.cos(angle), eyes.getY(), eyes.getZ() + Math.sin(angle));
        }
    }

    @Override
    public @NotNull GoalKey<Mob> getKey() {
        return key;
    }

    @Override
    public @NotNull EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.LOOK, GoalType.JUMP, GoalType.TARGET);
    }
}
