package com.andrei1058.bedwars.teamselector.teamselector

import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.team.ITeam
import com.andrei1058.bedwars.teamselector.api.events.TeamSelectorAbortEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * The teams the players of one arena asked for while the game was not started yet.
 * These are only wishes: real teams are given by [TeamSelectorAssigner] when the countdown ends.
 */
class ArenaPreferences internal constructor(val arena: IArena) {

    val selections: MutableMap<Player, ITeam> = HashMap()

    fun removePlayer(player: Player) {
        if (selections.remove(player) != null) {
            Bukkit.getPluginManager().callEvent(TeamSelectorAbortEvent(player))
        }
    }

    fun getTeam(player: Player): ITeam? = selections[player]

    /**
     * How many different teams were asked for.
     */
    fun getTeamsCount(): Int = selections.values.distinct().size

    /**
     * How many players made a choice.
     */
    fun getMembersCount(): Int = selections.size

    internal fun getPlayersCount(team: ITeam): Int = selections.values.count { it == team }

    fun getMembers(team: ITeam): List<Player> =
        selections.entries.filter { it.value == team }.map { it.key }

    fun setPlayerTeam(player: Player, team: ITeam) {
        selections[player] = team
    }
}
