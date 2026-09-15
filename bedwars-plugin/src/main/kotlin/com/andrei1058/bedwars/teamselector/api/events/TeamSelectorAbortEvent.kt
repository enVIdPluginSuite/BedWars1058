package com.andrei1058.bedwars.teamselector.api.events

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Called when a player leaves the arena and his data is removed from the team selector.
 */
class TeamSelectorAbortEvent(val player: Player) : Event() {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
