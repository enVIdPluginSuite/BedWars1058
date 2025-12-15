package com.andrei1058.bedwars.support.version.v1_21_R1.despawnable;

import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.api.server.VersionSupport;
import com.andrei1058.bedwars.support.version.v1_21_R1.ModernCompat;
import com.andrei1058.bedwars.support.version.v1_21_R1.paper.TeamMobGoal;
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
 * Spawns team silverfish and iron golems and gives them their BedWars behaviour.
 */
public final class DespawnableFactory {

    // Speeds the NMS goals used: the melee attack speed and the random stroll speed.
    private static final double GOLEM_MELEE_SPEED = 1.5d;
    private static final double GOLEM_STROLL_SPEED = 1d;
    private static final double SILVERFISH_MELEE_SPEED = 1.9d;
    private static final double SILVERFISH_STROLL_SPEED = 2d;

    private static final int HEALTH_SYMBOLS_IN_NAME = 10;

    private final VersionSupport versionSupport;
    private final Plugin plugin;
    private final HurtTracker hurtTracker;
    private final boolean paperGoalsAvailable = ModernCompat.isPaper();
    private boolean fallbackWarningShown = false;

    public DespawnableFactory(@NotNull VersionSupport versionSupport, @NotNull HurtTracker hurtTracker) {
        this.versionSupport = versionSupport;
        this.plugin = versionSupport.getPlugin();
        this.hurtTracker = hurtTracker;
    }

    public @NotNull LivingEntity spawn(@NotNull DespawnableAttributes attributes, @NotNull Location location,
                                       @NotNull ITeam team) {
        boolean isGolem = attributes.type() == DespawnableType.IRON_GOLEM;
        EntityType entityType = isGolem ? EntityType.IRON_GOLEM : EntityType.SILVERFISH;

        LivingEntity entity = (LivingEntity) Objects.requireNonNull(location.getWorld()).spawnEntity(location, entityType);
        applyDefaultSettings(entity, attributes, team);

        if (entity instanceof Mob mob) {
            double meleeSpeed = isGolem ? GOLEM_MELEE_SPEED : SILVERFISH_MELEE_SPEED;
            double strollSpeed = isGolem ? GOLEM_STROLL_SPEED : SILVERFISH_STROLL_SPEED;
            installBehaviour(mob, team, meleeSpeed, strollSpeed);
        }
        return entity;
    }

    /**
     * Makes the mob fight for its team. Paper replaces the whole vanilla AI; on other servers only the
     * target selection can be replaced, see {@link FallbackMobTask}.
     */
    private void installBehaviour(@NotNull Mob mob, @NotNull ITeam team, double meleeSpeed, double strollSpeed) {
        TeamTargeting targeting = new TeamTargeting(mob, team, versionSupport);

        if (paperGoalsAvailable) {
            try {
                TeamMobGoal.install(plugin, mob, targeting, hurtTracker, meleeSpeed, strollSpeed);
                return;
            } catch (Throwable paperApiProblem) {
                plugin.getLogger().log(Level.WARNING, "Could not apply custom mob goals, using the fallback.", paperApiProblem);
            }
        }

        if (!fallbackWarningShown) {
            fallbackWarningShown = true;
            plugin.getLogger().warning("The Paper mob goal API is not available: team golems and silverfish "
                    + "keep the vanilla movement AI. Use Paper for the exact behaviour.");
        }
        FallbackMobTask.start(plugin, targeting, hurtTracker);
    }

    /**
     * Initial name shown above the mob. The despawn countdown and health bar placeholders are refreshed every
     * second by {@code Despawnable#tick}, this only makes sure the first second already looks right.
     */
    private String displayName(@NotNull DespawnableAttributes attributes, @NotNull ITeam team) {
        Language language = Language.getDefaultLanguage();
        String nameTemplate = attributes.type() == DespawnableType.IRON_GOLEM
                ? Messages.SHOP_UTILITY_NPC_IRON_GOLEM_NAME
                : Messages.SHOP_UTILITY_NPC_SILVERFISH_NAME;

        String healthSymbol = language.m(Messages.FORMATTING_DESPAWNABLE_UTILITY_NPC_HEALTH) + " ";
        String fullHealthBar = healthSymbol.repeat(HEALTH_SYMBOLS_IN_NAME);

        return language.m(nameTemplate)
                .replace("{despawn}", String.valueOf(attributes.despawnSeconds()))
                .replace("{health}", fullHealthBar)
                .replace("{TeamColor}", team.getColor().chat().toString());
    }

    private void applyDefaultSettings(@NotNull LivingEntity entity, @NotNull DespawnableAttributes attributes,
                                      @NotNull ITeam team) {
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        entity.setCustomNameVisible(true);
        entity.setCustomName(displayName(attributes, team));

        setBaseValue(entity, "max_health", attributes.health());
        setBaseValue(entity, "movement_speed", attributes.speed());
        setBaseValue(entity, "attack_damage", attributes.damage());
    }

    /**
     * Attribute constants were renamed in 1.21.3, so they are looked up by registry name.
     */
    private void setBaseValue(@NotNull LivingEntity entity, @NotNull String attributeName, double value) {
        Attribute attribute = ModernCompat.attribute(attributeName);
        AttributeInstance attributeInstance = attribute == null ? null : entity.getAttribute(attribute);
        if (attributeInstance != null) {
            attributeInstance.setBaseValue(value);
        }
    }
}
