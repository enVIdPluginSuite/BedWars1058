package com.andrei1058.bedwars.listeners;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Keeps WorldEdit (and FastAsyncWorldEdit) out of matches: while a player is in an arena and is not in creative
 * mode, the WorldEdit wand and navigation wand do nothing and WorldEdit commands are refused.
 * Creative players are not restricted. Switch off with {@code world-edit.block-in-game: false}.
 */
public class WorldEditGuard implements Listener {

    private static boolean enabled() {
        return BedWars.config.getYml().getBoolean("world-edit.block-in-game", true);
    }

    private static boolean restricted(Player p) {
        if (p.getGameMode() == GameMode.CREATIVE) return false;
        return Arena.getArenaByPlayer(p) != null;
    }

    private static boolean hasWorldEdit() {
        return Bukkit.getPluginManager().getPlugin("WorldEdit") != null
                || Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
    }

    /** Material of a WorldEdit tool item as configured in WorldEdit ("minecraft:wooden_axe"), or the default. */
    private static Material configured(String field, String fallback) {
        String id = null;
        try {
            Object instance = Class.forName("com.sk89q.worldedit.WorldEdit").getMethod("getInstance").invoke(null);
            Object config = instance.getClass().getMethod("getConfiguration").invoke(instance);
            id = (String) config.getClass().getField(field).get(config);
        } catch (Throwable ignored) {
            // WorldEdit not (fully) there: use the defaults
        }
        if (id != null) {
            String name = id.toLowerCase(Locale.ROOT);
            if (name.startsWith("minecraft:")) name = name.substring("minecraft:".length());
            Material m = Material.matchMaterial(name);
            if (m != null) return m;
        }
        return Material.matchMaterial(fallback);
    }

    private static boolean isTool(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        if (type == Material.AIR) return false;
        Material wand = configured("wandItem", "WOODEN_AXE");
        if (wand == null) wand = Material.matchMaterial("WOOD_AXE");
        Material nav = configured("navigationWand", "COMPASS");
        return type == wand || type == nav;
    }

    /** WorldEdit skips an interaction whose item use was already denied, so the wand never gets to select anything. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (!enabled() || !hasWorldEdit()) return;
        Player p = e.getPlayer();
        if (!restricted(p)) return;
        if (!isTool(e.getItem())) return;
        e.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!enabled() || !hasWorldEdit()) return;
        Player p = e.getPlayer();
        if (!restricted(p)) return;

        String msg = e.getMessage().trim();
        String lower = msg.toLowerCase(Locale.ROOT);
        String label = lower.startsWith("/") ? lower.substring(1) : lower;
        int space = label.indexOf(' ');
        if (space >= 0) label = label.substring(0, space);

        boolean worldEdit = lower.startsWith("//")
                || label.equals("wand") || label.equals("we") || label.equals("worldedit")
                || label.equals("fawe") || label.equals("fastasyncworldedit")
                || label.startsWith("worldedit:") || label.startsWith("fastasyncworldedit:") || label.startsWith("fawe:");
        if (!worldEdit) return;

        e.setCancelled(true);
        p.sendMessage(ChatColor.RED + "WorldEdit is disabled during a match.");
    }
}
