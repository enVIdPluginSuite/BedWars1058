package com.andrei1058.bedwars.teamselector.teamselector

import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.team.ITeam
import org.bukkit.entity.Player

/**
 * Keeps the team preferences of every arena that has not started yet.
 */
object TeamManager {

    private val preferencesByArena: MutableMap<IArena, ArenaPreferences> = HashMap()

    @JvmStatic
    fun getInstance(): TeamManager = this

    /**
     * Forget the preferences of an arena, used once the teams are final.
     */
    fun clearArenaCache(arena: IArena) {
        preferencesByArena.remove(arena)
    }

    fun onQuit(arena: IArena, player: Player) {
        preferencesByArena[arena]?.removePlayer(player)
    }

    fun getPlayerTeam(player: Player, arena: IArena): ITeam? = getArena(arena).getTeam(player)

    fun getPlayersCount(team: ITeam, arena: IArena): Int = getArena(arena).getPlayersCount(team)

    fun getMembers(team: ITeam, arena: IArena): List<Player> = getArena(arena).getMembers(team)

    fun setPlayerTeam(player: Player, arena: IArena, team: ITeam) {
        getArena(arena).setPlayerTeam(player, team)
    }

    fun getArena(arena: IArena): ArenaPreferences =
        preferencesByArena.getOrPut(arena) { ArenaPreferences(arena) }
}
