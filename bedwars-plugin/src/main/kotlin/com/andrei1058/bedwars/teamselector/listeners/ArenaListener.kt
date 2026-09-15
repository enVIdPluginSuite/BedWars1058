package com.andrei1058.bedwars.teamselector.listeners

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.events.gameplay.GameStateChangeEvent
import com.andrei1058.bedwars.api.events.player.PlayerJoinArenaEvent
import com.andrei1058.bedwars.api.events.player.PlayerLeaveArenaEvent
import com.andrei1058.bedwars.teamselector.teamselector.TeamManager
import com.andrei1058.bedwars.teamselector.teamselector.TeamSelectorGUI
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class ArenaListener : Listener {

    @EventHandler
    fun onArenaJoin(event: PlayerJoinArenaEvent) {
        if (event.isCancelled || event.isSpectator) return
        val arena = event.arena ?: return
        if (arena.status != GameState.waiting && arena.status != GameState.starting) return

        // wait a moment, the player inventory is prepared after the join event
        Bukkit.getScheduler().runTaskLater(BedWars.plugin, Runnable {
            if (arena.isPlayer(event.player) || arena.status != GameState.playing) {
                TeamSelectorGUI.giveItem(event.player, null)
            }
        }, 30L)
    }

    /**
     * Forget the choice of a player who leaves.
     */
    @EventHandler
    fun onArenaLeave(event: PlayerLeaveArenaEvent) {
        val arena = event.arena
        if (arena.status == GameState.waiting || arena.status == GameState.starting) {
            TeamManager.onQuit(arena, event.player)
        }
    }

    @EventHandler
    fun onStatusChange(event: GameStateChangeEvent) {
        if (event.newState == GameState.starting) {
            // do not start when everybody picked the same single team
            val preferences = TeamManager.getArena(event.arena)
            val playersWithoutChoice = event.arena.players.size - preferences.getMembersCount()
            if (playersWithoutChoice <= 0 && preferences.getTeamsCount() == 1) {
                event.arena.setStatus(GameState.waiting)
            }
        }
        if (event.newState == GameState.playing || event.newState == GameState.restarting) {
            TeamManager.clearArenaCache(event.arena)
        }
    }
}
