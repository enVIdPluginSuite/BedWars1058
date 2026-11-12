package com.andrei1058.bedwars.party

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PartyListener : Listener {
    @EventHandler
    fun onJoin(e: PlayerJoinEvent) = PartyService.onJoin(e.player)

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) = PartyService.onQuit(e.player)

    /** Players who switched to party chat with `/party chat` keep talking to their party only. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(e: AsyncPlayerChatEvent) {
        if (!PartyService.usesPartyChat(e.player)) return
        e.isCancelled = true
        PartyService.chat(e.player, e.message)
    }
}
