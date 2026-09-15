package com.andrei1058.bedwars.teamselector.listeners

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.teamselector.configuration.Config
import com.andrei1058.bedwars.teamselector.teamselector.TeamSelectorGUI
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent

class InventoryListener : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val clickedItem = event.currentItem ?: return
        if (clickedItem.type == Material.AIR) return

        // nothing can be taken out of the selector GUI
        if (TeamSelectorGUI.openGUIs.contains(event.whoClicked.uniqueId)) event.isCancelled = true

        if (!BedWars.nms.isCustomBedWarsItem(clickedItem)) return
        val identifier = BedWars.nms.getCustomData(clickedItem) ?: return

        if (identifier == TeamSelectorGUI.TEAM_SELECTOR_IDENTIFIER) {
            event.isCancelled = !Config.config.getBoolean(Config.ALLOW_MOVE_TROUGH_INVENTORY)
        } else if (identifier.startsWith(TeamSelectorGUI.TEAM_JOIN_IDENTIFIER)) {
            val parts = identifier.split("_")
            if (parts.size != 2) return

            val player = event.whoClicked as? Player ?: return
            val joined = TeamSelectorGUI.joinTeam(player, parts[1])
            Config.playSound(player, if (joined) Config.SUCCESS_SOUND else Config.ERROR_SOUND)
            player.closeInventory()
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        TeamSelectorGUI.openGUIs.remove(event.player.uniqueId)
    }
}
