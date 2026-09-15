package com.andrei1058.bedwars.teamselector.listeners

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.teamselector.teamselector.TeamSelectorGUI
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

/**
 * Opens the GUI when the player right clicks with the selector item.
 */
class PlayerInteractListener : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return

        val itemInHand = BedWars.nms.getItemInHand(event.player) ?: return
        if (itemInHand.type == Material.AIR) return
        if (!BedWars.nms.isCustomBedWarsItem(itemInHand)) return

        if (BedWars.nms.getCustomData(itemInHand) == TeamSelectorGUI.TEAM_SELECTOR_IDENTIFIER) {
            event.isCancelled = true
            TeamSelectorGUI.openGUI(event.player, false)
        }
    }
}
