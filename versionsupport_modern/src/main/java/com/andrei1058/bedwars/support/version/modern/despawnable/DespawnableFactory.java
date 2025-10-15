package com.andrei1058.bedwars.support.version.modern.despawnable;

import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.api.server.VersionSupport;
import com.andrei1058.bedwars.support.version.modern.ModernCompat;
import com.andrei1058.bedwars.support.version.modern.paper.TeamMobGoal;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Spawns team silverfish and iron golems.
 */
public final class DespawnableFactory {

    private final VersionSupport versionSupport;
    private final Plugin plugin;
    private final HurtTracker hurtTracker;
    private final boolean paperGoals = ModernCompat.isPaper();
    private boolean warnedFallback = false;

    public DespawnableFactory(@NotNull VersionSupport versionSupport, @NotNull HurtTracker hurtTracker) {
        this.versionSupport = versionSupport;
        this.plugin = versionSupport.getPlugin();
        this.hurtTracker = hurtTracker;
    }

    public @NotNull LivingEntity spawn(@NotNull DespawnableAttributes attr, @NotNull Location location, @NotNull ITeam team) {
        boolean golem = attr.type() == DespawnableType.IRON_GOLEM;
        EntityType type = golem ? EntityType.IRON_GOLEM : EntityType.SILVERFISH;

        LivingEntity entity = (LivingEntity) Objects.requireNonNull(location.getWorld()).spawnEntity(location, type);
        applyDefaultSettings(entity, attr, team);

        if (entity instanceof Mob mob) {
            // speeds are the ones used by the NMS goals: melee attack speed and random stroll speed
            double meleeSpeed = golem ? 1.5d : 1.9d;
            double strollSpeed = golem ? 1d : 2d;
            TeamTargeting targeting = new TeamTargeting(mob, team, versionSupport);

            boolean installed = false;
            if (paperGoals) {
                try {
                    TeamMobGoal.install(plugin, mob, targeting, hurtTracker, meleeSpeed, strollSpeed);
                    installed = true;
                } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Could not apply custom mob goals, using the fallback.", t);
                }
            }
            if (!installed) {
                if (!warnedFallback) {
                    warnedFallback = true;
                    plugin.getLogger().warning("The Paper mob goal API is not available: team golems and silverfish "
                            + "keep the vanilla movement AI. Use Paper for the exact behaviour.");
                }
                FallbackMobTask.start(plugin, targeting, hurtTracker);
            }
        }
        return entity;
    }

    private String displayName(@NotNull DespawnableAttributes attr, @NotNull ITeam team) {
        Language lang = Language.getDefaultLanguage();
        String message = attr.type() == DespawnableType.IRON_GOLEM
                ? Messages.SHOP_UTILITY_NPC_IRON_GOLEM_NAME : Messages.SHOP_UTILITY_NPC_SILVERFISH_NAME;
        // placeholders are refreshed by Despawnable#tick, this only gives the initial text
        return lang.m(message).replace("{despawn}", String.valueOf(attr.despawnSeconds())
                .replace("{health}", (lang.m(Messages.FORMATTING_DESPAWNABLE_UTILITY_NPC_HEALTH) + " ").repeat(10))
                .replace("{TeamColor}", team.getColor().chat().toString())
        );
    }

    private void applyDefaultSettings(@NotNull LivingEntity entity, @NotNull DespawnableAttributes attr, @NotNull ITeam team) {
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        entity.setCustomNameVisible(true);
        entity.setCustomName(displayName(attr, team));

        setBase(entity, "max_health", attr.health());
        setBase(entity, "movement_speed", attr.speed());
        setBase(entity, "attack_damage", attr.damage());
    }

    private void setBase(@NotNull LivingEntity entity, @NotNull String attributeName, double value) {
        Attribute attribute = ModernCompat.attribute(attributeName);
        AttributeInstance instance = attribute == null ? null : entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
