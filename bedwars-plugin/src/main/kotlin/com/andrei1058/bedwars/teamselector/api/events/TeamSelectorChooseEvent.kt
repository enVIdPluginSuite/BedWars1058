package com.andrei1058.bedwars.teamselector.api.events

import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.team.ITeam
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Called when a player picks a team via the team selector.
 *
 * @param chosen the team the player wants to join.
 * @param oldChoice the previous choice, null if this is the player's first choice.
 */
class TeamSelectorChooseEvent(
    val player: Player,
    val arena: IArena,
    val chosen: ITeam,
    val oldChoice: ITeam?
) : Event() {

    private var cancelled = false

    fun isCancelled(): Boolean = cancelled

    /**
     * If you cancel it and the player already has a team, he remains in it.
     */
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
