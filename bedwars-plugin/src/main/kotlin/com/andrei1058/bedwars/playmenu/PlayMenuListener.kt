package com.andrei1058.bedwars.playmenu

import com.andrei1058.bedwars.BedWars
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent

/**
 * Keeps the play menu read only and forwards the clicks on its buttons.
 */
class PlayMenuListener : Listener {

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val holder = BedWars.nms.getTopInventory(player)?.holder as? PlayMenu.PlayMenuHolder ?: return

        // nothing can be taken out, moved in or shift clicked into the menu
        event.isCancelled = true

        // only clicks on the menu itself are buttons, not on the player's own inventory below it
        val clickedMenu = event.rawSlot in 0 until BedWars.nms.getTopInventory(player).size
        if (!clickedMenu) return

        PlayMenu.onButtonClick(player, holder.group, event.rawSlot)
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (BedWars.nms.getTopInventory(player)?.holder is PlayMenu.PlayMenuHolder) {
            event.isCancelled = true
        }
    }
}
