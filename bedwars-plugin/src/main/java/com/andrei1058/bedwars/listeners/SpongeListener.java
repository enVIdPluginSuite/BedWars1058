package com.andrei1058.bedwars.listeners;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Sponges are for soaking up water, not for building: a placed sponge disappears after a few seconds
 * ({@code sponge.despawn-seconds} in the main config) and can never be picked up again.
 */
public class SpongeListener implements Listener {

    private static boolean isSponge(Block b) {
        String n = b.getType().name();
        return n.equals("SPONGE") || n.equals("WET_SPONGE");
    }

    private static boolean isSponge(Material m) {
        return m.name().equals("SPONGE") || m.name().equals("WET_SPONGE");
    }

        /** Sponges that are counting down, by block position. */
    private static final Map<String, Countdown> ACTIVE = new HashMap<>();

    private static String key(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    private static void stop(Block b) {
        Countdown c = ACTIVE.remove(key(b));
        if (c != null) c.cancel();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!isSponge(e.getBlockPlaced().getType())) return;
        IArena arena = Arena.getArenaByPlayer(e.getPlayer());
        if (arena == null || arena.getStatus() != GameState.playing) return;

        Block block = e.getBlockPlaced();
        // a sponge placed on the spot of an earlier one starts its own countdown from zero
        stop(block);
        Countdown c = new Countdown(block, Math.max(1, BedWars.config.getYml().getInt("sponge.despawn-seconds", 3)) * 20);
        ACTIVE.put(key(block), c);
        c.start();
    }

    /** Burns while it waits to disappear, then removes the sponge. */
    private static final class Countdown implements Runnable {
        private static final int PERIOD = 3;
        private final Block block;
        private final int totalTicks;
        private int elapsed = 0;
        private BukkitTask task;

        Countdown(Block block, int totalTicks) {
            this.block = block;
            this.totalTicks = totalTicks;
        }

        void start() {
            task = Bukkit.getScheduler().runTaskTimer(BedWars.plugin, this, 0L, PERIOD);
        }

        void cancel() {
            if (task != null) task.cancel();
        }

        @Override
        public void run() {
            String k = key(block);
            if (ACTIVE.get(k) != this) { // replaced or stopped
                cancel();
                return;
            }
            IArena a = Arena.getArenaByIdentifier(block.getWorld().getName());
            if (a == null || !isSponge(block)) {
                ACTIVE.remove(k);
                cancel();
                return;
            }
            if (elapsed >= totalTicks) {
                ACTIVE.remove(k);
                cancel();
                block.setType(Material.AIR);
                a.removePlacedBlock(block);
                return;
            }
            flames(block, elapsed / (double) totalTicks);
            elapsed += PERIOD;
        }
    }

    private static Method spawnParticle;
    private static Object flame;
    private static boolean particleLookup;

    /** Fire particles around the block; more of them the closer the sponge is to disappearing. */
    private static void flames(Block b, double progress) {
        World w = b.getWorld();
        int count = 2 + (int) (progress * 6);
        if (!particleLookup) {
            particleLookup = true;
            try {
                Class<?> particle = Class.forName("org.bukkit.Particle");
                flame = particle.getField("FLAME").get(null);
                spawnParticle = World.class.getMethod("spawnParticle", particle, Location.class, int.class,
                        double.class, double.class, double.class, double.class);
            } catch (Throwable ignored) {
                spawnParticle = null; // old server: the classic flame effect is used instead
            }
        }
        Location center = b.getLocation().add(0.5, 0.5, 0.5);
        for (int i = 0; i < count; i++) {
            Location at = center.clone().add(
                    (Math.random() - 0.5) * 1.2, (Math.random() - 0.5) * 1.2, (Math.random() - 0.5) * 1.2);
            if (spawnParticle != null) {
                try {
                    spawnParticle.invoke(w, flame, at, 1, 0.0, 0.0, 0.0, 0.0);
                    continue;
                } catch (Throwable ignored) {
                    spawnParticle = null;
                }
            }
            w.playEffect(at, Effect.MOBSPAWNER_FLAMES, 0);
        }
    }

    /** A placed sponge that gets broken is simply gone, it does not drop an item. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isSponge(b)) return;
        IArena arena = Arena.getArenaByIdentifier(b.getWorld().getName());
        if (arena == null || !arena.isBlockPlaced(b)) return;
        e.setCancelled(true);
        stop(b);
        b.setType(Material.AIR);
        arena.removePlacedBlock(b);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        IArena arena = Arena.getArenaByIdentifier(e.getLocation().getWorld().getName());
        if (arena == null) return;
        Iterator<Block> it = e.blockList().iterator();
        while (it.hasNext()) {
            Block b = it.next();
            if (isSponge(b) && arena.isBlockPlaced(b)) {
                it.remove();
                stop(b);
                b.setType(Material.AIR);
                arena.removePlacedBlock(b);
            }
        }
    }
}
