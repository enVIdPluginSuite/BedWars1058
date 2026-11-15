package com.andrei1058.bedwars.listeners.arenaselector;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.configuration.Sounds;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class SpectateMenuListener implements Listener {

    public SpectateMenuListener() {
        SpectateMenu.registerMessages();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!(BedWars.nms.getTopInventory(player).getHolder() instanceof SpectateMenu.Holder)) return;
        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (!BedWars.nms.isCustomBedWarsItem(item)) return;
        String data = BedWars.nms.getCustomData(item);
        if (!data.startsWith(SpectateMenu.DATA_PREFIX)) return;

        IArena game = Arena.getArenaByIdentifier(data.substring(SpectateMenu.DATA_PREFIX.length()));
        player.closeInventory();
        if (game != null && game.getStatus() == GameState.playing && game.addSpectator(player, false, null)) {
            Sounds.playSound("spectate-allowed", player);
        } else {
            player.sendMessage(Language.getMsg(player, SpectateMenu.MSG_DENIED));
            Sounds.playSound("spectate-denied", player);
        }
    }
}
