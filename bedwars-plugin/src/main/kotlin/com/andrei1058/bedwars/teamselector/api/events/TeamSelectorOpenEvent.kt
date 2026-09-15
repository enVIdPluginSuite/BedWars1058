package com.andrei1058.bedwars.teamselector.api.events

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Called when a player opens the team selector GUI. Cancel it to keep the GUI closed.
 */
class TeamSelectorOpenEvent(val player: Player) : Event() {

    private var cancelled = false

    fun isCancelled(): Boolean = cancelled

    fun setCancelled(cancelled: Boolean) {
        this.cancelled = cancelled
    }

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
