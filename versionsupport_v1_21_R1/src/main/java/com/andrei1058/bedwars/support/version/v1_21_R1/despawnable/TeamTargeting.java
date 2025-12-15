package com.andrei1058.bedwars.support.version.v1_21_R1.despawnable;

import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.entity.Despawnable;
import com.andrei1058.bedwars.api.server.VersionSupport;
import com.andrei1058.bedwars.support.version.v1_21_R1.ModernCompat;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Target selection of team golems and silverfish.
 * <p>
 * It follows the vanilla {@code NearestAttackableTargetGoal} (random interval of 20 ticks, must see the target,
 * search box of follow range x 4 blocks vertically, range reduced for sneaking/invisible targets) filtered by the
 * same predicate the NMS based versions used:
 * <ul>
 *     <li>players: alive, not a member of the team, not re-spawning, not spectating;</li>
 *     <li>anything else: not a despawnable of the same team.</li>
 * </ul>
 */
public final class TeamTargeting {

    /** Average number of ticks between two searches for a target. */
    private static final int SEARCH_INTERVAL_TICKS = 20;
    /** Ticks a target may stay out of sight before the mob gives up on it. */
    private static final int MAX_UNSEEN_TICKS = 60;
    /** Vertical half-size of the search box, horizontally the follow range is used. */
    private static final double SEARCH_HEIGHT = 4.0d;
    private static final double MIN_NOTICE_DISTANCE = 2.0d;

    private final Mob mob;
    private final ITeam team;
    private final VersionSupport versionSupport;
    private final Attribute followRange = ModernCompat.attribute("follow_range");

    private int unseenTicks = 0;

    public TeamTargeting(@NotNull Mob mob, @NotNull ITeam team, @NotNull VersionSupport versionSupport) {
        this.mob = mob;
        this.team = team;
        this.versionSupport = versionSupport;
    }

    public @NotNull Mob getMob() {
        return mob;
    }

    private double followDistance() {
        if (followRange != null) {
            AttributeInstance instance = mob.getAttribute(followRange);
            if (instance != null) {
                return instance.getValue();
            }
        }
        return 16d;
    }

    /**
     * The BedWars specific rule: who counts as an enemy of this team's mob.
     */
    boolean isEnemy(@NotNull LivingEntity entity) {
        if (entity instanceof Player player) {
            UUID playerId = player.getUniqueId();
            boolean isOutOfTheFight = team.getArena().isReSpawning(playerId) || team.getArena().isSpectator(playerId);
            return !player.isDead() && !team.wasMember(playerId) && !isOutOfTheFight;
        }
        return !isMobOfSameTeam(entity);
    }

    private boolean isMobOfSameTeam(@NotNull Entity entity) {
        Despawnable despawnable = versionSupport.getDespawnablesList().get(entity.getUniqueId());
        return despawnable != null && despawnable.getTeam() == team;
    }

    /**
     * Vanilla rules for what a mob may attack at all, regardless of teams.
     */
    private boolean isAttackable(@NotNull LivingEntity entity) {
        if (entity.equals(mob)) return false;
        if (!entity.isValid() || entity.isDead()) return false;
        if (entity.isInvulnerable()) return false;
        if (entity instanceof ArmorStand) return false;
        if (entity instanceof Player player) {
            GameMode gameMode = player.getGameMode();
            if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) return false;
        }
        return entity.getWorld().equals(mob.getWorld());
    }

    /**
     * Mirrors the vanilla visibility factor: sneaking and invisible targets are noticed from a shorter distance.
     * Invisible targets are easier to notice the more armor they wear.
     */
    private double visibilityFactor(@NotNull LivingEntity target) {
        double factor = 1.0d;
        if (target.isSneaking()) {
            factor *= 0.8d;
        }
        if (target.isInvisible()) {
            double armorCover = Math.max(armorCoverage(target), 0.1d);
            factor *= 0.7d * armorCover;
        }
        return factor;
    }

    /**
     * @return share of armor slots that are filled, from 0 (naked) to 1 (full set).
     */
    private double armorCoverage(@NotNull LivingEntity target) {
        EntityEquipment equipment = target.getEquipment();
        if (equipment == null) {
            return 0d;
        }
        ItemStack[] armorSlots = equipment.getArmorContents();
        int wornPieces = 0;
        for (ItemStack piece : armorSlots) {
            if (piece != null && !piece.getType().isAir()) {
                wornPieces++;
            }
        }
        return wornPieces / (double) Math.max(1, armorSlots.length);
    }

    private boolean isWithinNoticeRange(@NotNull LivingEntity target) {
        double noticeDistance = Math.max(followDistance() * visibilityFactor(target), MIN_NOTICE_DISTANCE);
        return mob.getLocation().distanceSquared(target.getLocation()) <= noticeDistance * noticeDistance;
    }

    private boolean isValidNewTarget(@NotNull LivingEntity candidate) {
        return isAttackable(candidate) && isEnemy(candidate)
                && isWithinNoticeRange(candidate) && mob.hasLineOfSight(candidate);
    }

    /**
     * Called every tick while the mob is alive. Keeps or drops the current target and looks for a new one.
     *
     * @param lastAttacker last living entity that damaged the mob (consumed by the caller), can be null.
     */
    public void update(@Nullable LivingEntity lastAttacker) {
        LivingEntity currentTarget = mob.getTarget();

        // Retaliating against whoever hurt the mob has the highest priority (vanilla HurtByTargetGoal).
        boolean shouldRetaliate = lastAttacker != null && !lastAttacker.equals(currentTarget)
                && isAttackable(lastAttacker) && isEnemy(lastAttacker);
        if (shouldRetaliate) {
            changeTarget(lastAttacker);
            return;
        }

        if (currentTarget != null) {
            keepOrDropTarget(currentTarget);
            return;
        }

        searchForNewTarget();
    }

    /**
     * Vanilla TargetGoal#canContinueToUse: drop targets that became invalid, left the follow range or have
     * been out of sight for too long.
     */
    private void keepOrDropTarget(@NotNull LivingEntity target) {
        if (!isAttackable(target) || !isEnemy(target)) {
            changeTarget(null);
            return;
        }

        double followDistance = followDistance();
        boolean outOfRange = mob.getLocation().distanceSquared(target.getLocation()) > followDistance * followDistance;
        if (outOfRange) {
            changeTarget(null);
            return;
        }

        if (mob.hasLineOfSight(target)) {
            unseenTicks = 0;
        } else {
            unseenTicks++;
            if (unseenTicks > MAX_UNSEEN_TICKS) {
                changeTarget(null);
            }
        }
    }

    /**
     * Vanilla NearestAttackableTargetGoal#canUse: on average once every {@link #SEARCH_INTERVAL_TICKS} ticks
     * pick the closest valid enemy around.
     */
    private void searchForNewTarget() {
        boolean searchThisTick = ThreadLocalRandom.current().nextInt(SEARCH_INTERVAL_TICKS) == 0;
        if (!searchThisTick) {
            return;
        }
        LivingEntity nearestEnemy = findNearestEnemy();
        if (nearestEnemy != null) {
            changeTarget(nearestEnemy);
        }
    }

    private void changeTarget(@Nullable LivingEntity newTarget) {
        mob.setTarget(newTarget);
        unseenTicks = 0;
    }

    private @Nullable LivingEntity findNearestEnemy() {
        double searchRadius = followDistance();
        LivingEntity nearestEnemy = null;
        double nearestDistanceSquared = Double.MAX_VALUE;

        for (Entity nearbyEntity : mob.getNearbyEntities(searchRadius, SEARCH_HEIGHT, searchRadius)) {
            if (!(nearbyEntity instanceof LivingEntity candidate)) continue;
            if (!isValidNewTarget(candidate)) continue;

            double distanceSquared = mob.getLocation().distanceSquared(candidate.getLocation());
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearestEnemy = candidate;
            }
        }
        return nearestEnemy;
    }
}
