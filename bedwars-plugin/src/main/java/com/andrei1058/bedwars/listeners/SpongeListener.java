package com.andrei1058.bedwars.listeners;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!isSponge(e.getBlockPlaced().getType())) return;
        IArena arena = Arena.getArenaByPlayer(e.getPlayer());
        if (arena == null || arena.getStatus() != GameState.playing) return;

        final Block block = e.getBlockPlaced();
        final String worldName = block.getWorld().getName();
        long ticks = Math.max(1, BedWars.config.getYml().getInt("sponge.despawn-seconds", 3)) * 20L;
        Bukkit.getScheduler().runTaskLater(BedWars.plugin, () -> {
            IArena a = Arena.getArenaByIdentifier(worldName);
            if (a == null || !isSponge(block)) return;
            block.setType(Material.AIR);
            a.removePlacedBlock(block);
        }, ticks);
    }

    /** A placed sponge that gets broken is simply gone, it does not drop an item. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!isSponge(b)) return;
        IArena arena = Arena.getArenaByIdentifier(b.getWorld().getName());
        if (arena == null || !arena.isBlockPlaced(b)) return;
        e.setCancelled(true);
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
                b.setType(Material.AIR);
                arena.removePlacedBlock(b);
            }
        }
    }
}
