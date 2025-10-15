package com.andrei1058.bedwars.support.version.modern.paper;

import com.andrei1058.bedwars.support.version.modern.despawnable.HurtTracker;
import com.andrei1058.bedwars.support.version.modern.despawnable.TeamTargeting;
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
    private static final int STROLL_INTERVAL = 120;

    private final Mob mob;
    private final TeamTargeting targeting;
    private final HurtTracker hurtTracker;
    private final double meleeSpeed;
    private final double strollSpeed;
    private final GoalKey<Mob> key;

    private int attackCooldown = 0;
    private int pathRecalculation = 0;

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

        floatInLiquid();

        targeting.update(hurtTracker.consume(mob.getUniqueId()));

        LivingEntity target = mob.getTarget();
        if (target != null && target.isValid()) {
            melee(target);
        } else {
            attackCooldown = 0;
            pathRecalculation = 0;
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
        if ((mob.isInWater() || mob.isInLava()) && ThreadLocalRandom.current().nextFloat() < 0.8f) {
            Vector velocity = mob.getVelocity();
            mob.setVelocity(velocity.setY(velocity.getY() + 0.04d));
        }
    }

    /**
     * MeleeAttackGoal.
     */
    private void melee(@NotNull LivingEntity target) {
        Pathfinder pathfinder = mob.getPathfinder();
        mob.lookAt(target, 30f, 30f);

        if (pathRecalculation-- <= 0) {
            if (mob.hasLineOfSight(target)) {
                pathRecalculation = 4 + ThreadLocalRandom.current().nextInt(7);
                double distanceSquared = mob.getLocation().distanceSquared(target.getLocation());
                if (distanceSquared > 1024d) {
                    pathRecalculation += 10;
                } else if (distanceSquared > 256d) {
                    pathRecalculation += 5;
                }
                if (!pathfinder.moveTo(target, meleeSpeed)) {
                    pathRecalculation += 15;
                }
            } else {
                pathRecalculation = 0;
            }
        }

        if (attackCooldown > 0) {
            attackCooldown--;
        }
        double reach = mob.getWidth() * 2.0d;
        double reachSquared = reach * reach + target.getWidth();
        if (attackCooldown <= 0
                && mob.getLocation().distanceSquared(target.getLocation()) <= reachSquared
                && mob.hasLineOfSight(target)) {
            attackCooldown = ATTACK_COOLDOWN_TICKS;
            mob.swingMainHand();
            mob.attack(target);
        }
    }

    /**
     * RandomStrollGoal and RandomLookAroundGoal.
     */
    private void idle() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Pathfinder pathfinder = mob.getPathfinder();

        if (!pathfinder.hasPath() && random.nextInt(STROLL_INTERVAL) == 0) {
            Location destination = mob.getLocation().add(
                    random.nextInt(-10, 11),
                    random.nextInt(-7, 8),
                    random.nextInt(-10, 11)
            );
            pathfinder.moveTo(destination, strollSpeed);
        } else if (random.nextFloat() < 0.02f) {
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
