package com.andrei1058.bedwars.support.version.v1_21_R1.despawnable;

import com.andrei1058.bedwars.api.server.VersionSupport;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers who hurt a team golem/silverfish so it can retaliate (vanilla HurtByTargetGoal).
 */
public final class HurtTracker implements Listener {

    private final VersionSupport api;
    private final Map<UUID, LivingEntity> lastAttacker = new ConcurrentHashMap<>();

    public HurtTracker(@NotNull VersionSupport api) {
        this.api = api;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        UUID victim = e.getEntity().getUniqueId();
        if (!api.getDespawnablesList().containsKey(victim)) return;

        Entity damager = e.getDamager();
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            damager = shooter instanceof Entity entity ? entity : null;
        }
        if (damager instanceof LivingEntity living) {
            lastAttacker.put(victim, living);
        }
    }

    /**
     * @return the last attacker, only once.
     */
    public @Nullable LivingEntity consume(@NotNull UUID victim) {
        return lastAttacker.remove(victim);
    }

    public void forget(@NotNull UUID victim) {
        lastAttacker.remove(victim);
    }
}
