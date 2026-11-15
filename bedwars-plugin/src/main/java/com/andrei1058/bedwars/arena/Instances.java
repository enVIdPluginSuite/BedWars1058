package com.andrei1058.bedwars.arena;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.configuration.ConfigPath;

import java.util.List;

/**
 * Helpers for running several simultaneous instances (games) of the same arena.
 * Every instance owns a separate world copy, named by {@link ArenaManager#generateGameID()}
 * except the first one which keeps the arena's own world name on non auto-scale servers.
 */
public final class Instances {

    public static final String ENABLED_PATH = "multi-instance.enabled";
    public static final String MAX_PER_ARENA_PATH = "multi-instance.max-per-arena";

    private Instances() {
    }

    /**
     * @return true if more than one game of the same arena may exist at once.
     */
    public static boolean enabled() {
        if (BedWars.autoscale) return true;
        // legacy bungee runs a single arena per server
        if (BedWars.getServerType() == com.andrei1058.bedwars.api.server.ServerType.BUNGEE) return false;
        return BedWars.config.getYml().getBoolean(ENABLED_PATH, true);
    }

    /**
     * @return max simultaneous instances of one arena.
     */
    public static int limit() {
        if (BedWars.autoscale) {
            return BedWars.config.getInt(ConfigPath.GENERAL_CONFIGURATION_AUTO_SCALE_LIMIT);
        }
        return BedWars.config.getYml().getInt(MAX_PER_ARENA_PATH, 5);
    }

    /**
     * Pick the world name for a new instance of an arena.
     */
    public static String pickWorldName(String arenaName) {
        if (BedWars.autoscale) {
            return BedWars.arenaManager.generateGameID();
        }
        if (!enabled()) {
            return arenaName;
        }
        for (IArena a : Arena.getEnableQueue()) {
            if (a.getArenaName().equalsIgnoreCase(arenaName)) {
                return BedWars.arenaManager.generateGameID();
            }
        }
        if (!Arena.getArenasByName(arenaName).isEmpty()) {
            return BedWars.arenaManager.generateGameID();
        }
        return arenaName;
    }

    public static boolean isJoinable(IArena a) {
        GameState s = a.getStatus();
        return (s == GameState.waiting || s == GameState.starting) && a.getPlayers().size() < a.getMaxPlayers();
    }

    /**
     * The instance a player should be sent to: the fullest joinable one,
     * otherwise the oldest one.
     */
    public static IArena best(List<IArena> instances) {
        IArena best = null;
        for (IArena a : instances) {
            if (!isJoinable(a)) continue;
            if (best == null || a.getPlayers().size() > best.getPlayers().size()) best = a;
        }
        if (best != null) return best;
        return instances.isEmpty() ? null : instances.get(0);
    }
}
