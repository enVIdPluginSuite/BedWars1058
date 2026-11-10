package com.andrei1058.bedwars.listeners;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.arena.Arena;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * Two jobs for arena worlds. (1) Chunks that lie completely outside the world border (many maps have one) hold no part
 * of the map, so whatever terrain they contain is cleared as they load. (2) Diagnostic: arena maps must be created with
 * a void chunk generator. When a world that belongs to a queued arena is created without one (vanilla terrain would
 * show up around the map), this logs which code created it.
 *
 * Original description: Diagnostic only: arena maps must be created with a void chunk generator. When a world that belongs to a queued
 * arena is created without one (vanilla terrain would show up around the map), this logs which code created it.
 * Nothing is changed in the world.
 */
public class VoidGuardListener implements Listener {

    private final Set<String> warned = new HashSet<>();

    private boolean isArenaWorld(World world) {
        String name = world.getName();
        if (Arena.getArenaByIdentifier(name) != null) return true;
        for (IArena a : Arena.getEnableQueue()) {
            if (name.equalsIgnoreCase(a.getWorldName()) || name.equalsIgnoreCase(a.getArenaName())) return true;
        }
        return false;
    }

    private static int minHeight(World world) {
        try {
            return (int) World.class.getMethod("getMinHeight").invoke(world);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Clears chunks that are entirely outside the world border (with one chunk of margin). Only applies when a real
     * border is set; the default border is so large that it never matches.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        if (!BedWars.config.getYml().getBoolean("void-guard.clear-outside-border", true)) return;
        World world = e.getWorld();
        WorldBorder border;
        try {
            border = world.getWorldBorder();
        } catch (Throwable t) {
            return;
        }
        double size = border.getSize();
        if (size >= 1.0E6 || !isArenaWorld(world)) return;

        Location c = border.getCenter();
        double half = size / 2.0 + 16.0;
        Chunk chunk = e.getChunk();
        int minX = chunk.getX() << 4, minZ = chunk.getZ() << 4;
        boolean outside = minX > c.getX() + half || minX + 15 < c.getX() - half
                || minZ > c.getZ() + half || minZ + 15 < c.getZ() - half;
        if (!outside) return;

        // next tick: clearing inside the load event could interfere with whatever is loading the chunk
        BedWars.plugin.getServer().getScheduler().runTask(BedWars.plugin, () -> clear(world, chunk));
    }

    private void clear(World world, Chunk chunk) {
        if (!chunk.isLoaded()) return;
        int bottom = minHeight(world);
        int top = world.getMaxHeight();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = bottom; y < top; y++) {
                    org.bukkit.block.Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.AIR) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldInit(WorldInitEvent e) {
        World world = e.getWorld();
        if (world.getGenerator() != null) return;
        String name = world.getName();

        boolean arenaWorld = false;
        for (IArena a : Arena.getEnableQueue()) {
            if (name.equalsIgnoreCase(a.getWorldName()) || name.equalsIgnoreCase(a.getArenaName())) {
                arenaWorld = true;
                break;
            }
        }
        if (!arenaWorld || !warned.add(name.toLowerCase())) return;

        StringWriter trace = new StringWriter();
        new Throwable("world created here").printStackTrace(new PrintWriter(trace));
        BedWars.plugin.getLogger().warning("The world '" + name + "' of an arena is being created WITHOUT a void chunk generator, " +
                "so vanilla terrain will generate around the map. Created by:\n" + trace);
    }
}
