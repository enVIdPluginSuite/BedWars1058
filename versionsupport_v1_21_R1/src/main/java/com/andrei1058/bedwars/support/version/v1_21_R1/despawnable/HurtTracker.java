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

    private final VersionSupport versionSupport;
    private final Map<UUID, LivingEntity> lastAttacker = new ConcurrentHashMap<>();

    public HurtTracker(@NotNull VersionSupport versionSupport) {
        this.versionSupport = versionSupport;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        UUID victimId = event.getEntity().getUniqueId();
        boolean victimIsTeamMob = versionSupport.getDespawnablesList().containsKey(victimId);
        if (!victimIsTeamMob) return;

        Entity attacker = event.getDamager();
        if (attacker instanceof Projectile projectile) {
            // blame whoever shot it, not the arrow
            ProjectileSource shooter = projectile.getShooter();
            attacker = shooter instanceof Entity shooterEntity ? shooterEntity : null;
        }
        if (attacker instanceof LivingEntity livingAttacker) {
            lastAttacker.put(victimId, livingAttacker);
        }
    }

    /**
     * @return the last attacker, only once.
     */
    public @Nullable LivingEntity consume(@NotNull UUID victimId) {
        return lastAttacker.remove(victimId);
    }

    public void forget(@NotNull UUID victimId) {
        lastAttacker.remove(victimId);
    }
}
