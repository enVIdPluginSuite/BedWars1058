package com.andrei1058.bedwars.support.version.modern.despawnable;

import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

/**
 * Used on servers without the Paper mob goal API (plain Spigot). Without NMS the vanilla goals cannot be removed,
 * so movement and attacks stay vanilla and only the target selection is driven here.
 */
public final class FallbackMobTask extends BukkitRunnable {

    private final TeamTargeting targeting;
    private final HurtTracker hurtTracker;

    private FallbackMobTask(@NotNull TeamTargeting targeting, @NotNull HurtTracker hurtTracker) {
        this.targeting = targeting;
        this.hurtTracker = hurtTracker;
    }

    public static void start(@NotNull Plugin plugin, @NotNull TeamTargeting targeting, @NotNull HurtTracker hurtTracker) {
        new FallbackMobTask(targeting, hurtTracker).runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void run() {
        Mob mob = targeting.getMob();
        if (!mob.isValid()) {
            hurtTracker.forget(mob.getUniqueId());
            cancel();
            return;
        }
        targeting.update(hurtTracker.consume(mob.getUniqueId()));
    }
}
