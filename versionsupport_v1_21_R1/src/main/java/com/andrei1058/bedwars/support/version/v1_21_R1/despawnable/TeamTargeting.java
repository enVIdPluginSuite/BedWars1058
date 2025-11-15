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

    private static final int RANDOM_INTERVAL = 20;
    private static final int MAX_UNSEEN_TICKS = 60;

    private final Mob mob;
    private final ITeam team;
    private final VersionSupport api;
    private final Attribute followRange = ModernCompat.attribute("follow_range");

    private int unseenTicks = 0;

    public TeamTargeting(@NotNull Mob mob, @NotNull ITeam team, @NotNull VersionSupport api) {
        this.mob = mob;
        this.team = team;
        this.api = api;
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
     * The BedWars specific filter.
     */
    boolean predicate(@NotNull LivingEntity entity) {
        if (entity instanceof Player player) {
            UUID id = player.getUniqueId();
            return !player.isDead()
                    && !team.wasMember(id)
                    && !team.getArena().isReSpawning(id)
                    && !team.getArena().isSpectator(id);
        }
        return notSameTeam(entity);
    }

    private boolean notSameTeam(@NotNull Entity entity) {
        Despawnable despawnable = api.getDespawnablesList().getOrDefault(entity.getUniqueId(), null);
        return null == despawnable || despawnable.getTeam() != team;
    }

    /**
     * Vanilla "can be seen as enemy" plus attackable checks.
     */
    private boolean attackable(@NotNull LivingEntity entity) {
        if (entity.equals(mob)) return false;
        if (!entity.isValid() || entity.isDead()) return false;
        if (entity.isInvulnerable()) return false;
        if (entity instanceof ArmorStand) return false;
        if (entity instanceof Player player) {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false;
        }
        return entity.getWorld().equals(mob.getWorld());
    }

    /**
     * Mirrors Entity#getVisibilityPercent.
     */
    private double visibility(@NotNull LivingEntity target) {
        double percent = 1.0d;
        if (target.isSneaking()) {
            percent *= 0.8d;
        }
        if (target.isInvisible()) {
            double cover = 0;
            EntityEquipment equipment = target.getEquipment();
            if (equipment != null) {
                ItemStack[] armor = equipment.getArmorContents();
                int worn = 0;
                for (ItemStack piece : armor) {
                    if (piece != null && !piece.getType().isAir()) worn++;
                }
                cover = worn / (double) Math.max(1, armor.length);
            }
            if (cover < 0.1d) cover = 0.1d;
            percent *= 0.7d * cover;
        }
        return percent;
    }

    private boolean inRange(@NotNull LivingEntity target) {
        double range = Math.max(followDistance() * visibility(target), 2.0d);
        return mob.getLocation().distanceSquared(target.getLocation()) <= range * range;
    }

    private boolean valid(@NotNull LivingEntity target, boolean needSight) {
        if (!attackable(target) || !predicate(target)) return false;
        if (!inRange(target)) return false;
        return !needSight || mob.hasLineOfSight(target);
    }

    /**
     * Called every tick while the mob is alive. Keeps/drops the current target and looks for a new one.
     *
     * @param hurtBy last living entity that damaged the mob (consumed), can be null.
     */
    public void update(@Nullable LivingEntity hurtBy) {
        LivingEntity current = mob.getTarget();

        // HurtByTargetGoal has the highest priority
        if (hurtBy != null && !hurtBy.equals(current) && attackable(hurtBy) && predicate(hurtBy)) {
            mob.setTarget(hurtBy);
            unseenTicks = 0;
            return;
        }

        if (current != null) {
            // TargetGoal#canContinueToUse
            if (!attackable(current) || !predicate(current)) {
                mob.setTarget(null);
                unseenTicks = 0;
                return;
            }
            double distance = followDistance();
            if (mob.getLocation().distanceSquared(current.getLocation()) > distance * distance) {
                mob.setTarget(null);
                unseenTicks = 0;
                return;
            }
            if (mob.hasLineOfSight(current)) {
                unseenTicks = 0;
            } else if (++unseenTicks > MAX_UNSEEN_TICKS) {
                mob.setTarget(null);
                unseenTicks = 0;
            }
            return;
        }

        // NearestAttackableTargetGoal#canUse
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(RANDOM_INTERVAL) != 0) {
            return;
        }
        LivingEntity nearest = findNearest();
        if (nearest != null) {
            mob.setTarget(nearest);
            unseenTicks = 0;
        }
    }

    private @Nullable LivingEntity findNearest() {
        double range = followDistance();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mob.getNearbyEntities(range, 4.0d, range)) {
            if (!(entity instanceof LivingEntity candidate)) continue;
            if (!valid(candidate, true)) continue;
            double distance = mob.getLocation().distanceSquared(candidate.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
