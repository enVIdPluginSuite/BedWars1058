package com.andrei1058.bedwars.listeners;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.arena.Arena;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

/**
 * Diagnostic only: arena maps must be created with a void chunk generator. When a world that belongs to a queued
 * arena is created without one (vanilla terrain would show up around the map), this logs which code created it.
 * Nothing is changed in the world.
 */
public class VoidGuardListener implements Listener {

    private final Set<String> warned = new HashSet<>();

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
