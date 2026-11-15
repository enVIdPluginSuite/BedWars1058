package com.andrei1058.bedwars.listeners.arenaselector;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.configuration.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lobby menu used to pick which running game of an arena to spectate.
 * Every running instance of every arena has its own world, so there can be
 * several games to choose from for the same map.
 */
public final class SpectateMenu {

    public static final String DATA_PREFIX = "spectate=";

    public static final String MSG_TITLE = "spectate-menu-name";
    public static final String MSG_ITEM_NAME = "spectate-menu-item-name";
    public static final String MSG_ITEM_LORE = "spectate-menu-item-lore";
    public static final String MSG_NONE = "spectate-menu-none";
    public static final String MSG_DENIED = "spectate-menu-denied";
    public static final String MSG_ARENA_UNKNOWN = "spectate-menu-unknown-arena";

    private SpectateMenu() {
    }

    /**
     * Adds the default texts to every language file that doesn't have them yet.
     */
    public static void registerMessages() {
        Language.saveIfNotExists(MSG_TITLE, "&8Spectate a game");
        Language.saveIfNotExists(MSG_ITEM_NAME, "&a{arena} &7#{number}");
        Language.saveIfNotExists(MSG_ITEM_LORE, Arrays.asList(
                "&7Players: &f{on}&7/&f{max}",
                "&7Teams alive: &f{teams}",
                "&7Running for: &f{time}",
                "",
                "&eClick to spectate!"));
        Language.saveIfNotExists(MSG_NONE, "&c▪ &7There are no games to spectate right now.");
        Language.saveIfNotExists(MSG_DENIED, "&c▪ &7You can't spectate that game.");
        Language.saveIfNotExists(MSG_ARENA_UNKNOWN, "&c▪ &7There is no arena called &f{name}&7.");
    }

    /**
     * Running games that can be spectated.
     *
     * @param arenaName only games of this arena, or null for every arena.
     */
    public static List<IArena> spectatable(String arenaName) {
        List<IArena> list = new ArrayList<>();
        for (IArena a : Arena.getArenas()) {
            if (a.getStatus() != GameState.playing) continue;
            if (!a.isAllowSpectate()) continue;
            if (arenaName != null && !a.getArenaName().equalsIgnoreCase(arenaName)) continue;
            list.add(a);
        }
        return list;
    }

    /**
     * Opens the menu.
     *
     * @param arenaName limit to one arena, or null for every arena.
     * @return false when there is nothing to spectate.
     */
    public static boolean open(Player p, String arenaName) {
        List<IArena> games = spectatable(arenaName);
        if (games.isEmpty()) {
            p.sendMessage(Language.getMsg(p, MSG_NONE));
            Sounds.playSound("spectate-denied", p);
            return false;
        }
        // a single game needs no menu
        if (games.size() == 1 && arenaName != null) {
            if (games.get(0).addSpectator(p, false, null)) {
                Sounds.playSound("spectate-allowed", p);
            } else {
                p.sendMessage(Language.getMsg(p, MSG_DENIED));
                Sounds.playSound("spectate-denied", p);
            }
            return true;
        }

        int size = Math.min(54, Math.max(9, ((games.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(new Holder(arenaName), size, Language.getMsg(p, MSG_TITLE));
        String material = BedWars.config.getString(ConfigPath.GENERAL_CONFIGURATION_ARENA_SELECTOR_STATUS_MATERIAL.replace("%path%", "playing"));
        short data = (short) BedWars.config.getInt(ConfigPath.GENERAL_CONFIGURATION_ARENA_SELECTOR_STATUS_DATA.replace("%path%", "playing"));

        // number games per arena so players can tell them apart
        java.util.Map<String, Integer> counters = new java.util.HashMap<>();
        for (int slot = 0; slot < games.size() && slot < size; slot++) {
            IArena g = games.get(slot);
            int number = counters.merge(g.getArenaName(), 1, Integer::sum);
            ItemStack item = BedWars.nms.createItemStack(material, 1, data);
            ItemMeta im = item.getItemMeta();
            im.setDisplayName(fill(Language.getMsg(p, MSG_ITEM_NAME), g, number));
            List<String> lore = new ArrayList<>();
            for (String line : Language.getList(p, MSG_ITEM_LORE)) {
                lore.add(fill(line, g, number));
            }
            im.setLore(lore);
            item.setItemMeta(im);
            item = BedWars.nms.addCustomData(item, DATA_PREFIX + g.getWorldName());
            inv.setItem(slot, item);
        }
        p.openInventory(inv);
        Sounds.playSound("arena-selector-open", p);
        return true;
    }

    private static String fill(String s, IArena g, int number) {
        int alive = 0;
        for (com.andrei1058.bedwars.api.arena.team.ITeam t : g.getTeams()) {
            if (!t.getMembers().isEmpty()) alive++;
        }
        return s.replace("{arena}", g.getDisplayName())
                .replace("{map_name}", g.getArenaName())
                .replace("{number}", String.valueOf(number))
                .replace("{world}", g.getWorldName())
                .replace("{on}", String.valueOf(g.getPlayers().size()))
                .replace("{max}", String.valueOf(g.getMaxPlayers()))
                .replace("{teams}", String.valueOf(alive))
                .replace("{time}", elapsed(g.getStartTime()));
    }

    private static String elapsed(Instant start) {
        if (start == null) return "0:00";
        Duration d = Duration.between(start, Instant.now());
        long secs = Math.max(0, d.getSeconds());
        return (secs / 60) + ":" + String.format("%02d", secs % 60);
    }

    public static final class Holder implements InventoryHolder {
        private final String arenaName;

        Holder(String arenaName) {
            this.arenaName = arenaName;
        }

        public String getArenaName() {
            return arenaName;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
