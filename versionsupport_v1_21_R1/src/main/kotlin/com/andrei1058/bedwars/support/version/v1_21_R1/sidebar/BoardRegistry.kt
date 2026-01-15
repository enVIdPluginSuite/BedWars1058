package com.andrei1058.bedwars.support.version.v1_21_R1.sidebar

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps one private [Scoreboard] per player. Sidebar, health objective and tab teams are all
 * registered on the receiver's own board, so nothing leaks into other players' views or the main scoreboard.
 */
internal object BoardRegistry {

    private val boards = ConcurrentHashMap<UUID, Scoreboard>()

    fun get(player: Player): Scoreboard {
        val board = boards.getOrPut(player.uniqueId) { Bukkit.getScoreboardManager().newScoreboard }
        if (player.scoreboard !== board) {
            player.scoreboard = board
        }
        return board
    }

    /**
     * Hands the main scoreboard back once nothing of ours is left on the player's board.
     */
    fun releaseIfEmpty(player: Player) {
        val board = boards[player.uniqueId] ?: return
        if (board.objectives.isEmpty() && board.teams.isEmpty()) {
            boards.remove(player.uniqueId)
            if (player.isOnline && player.scoreboard === board) {
                player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
            }
        }
    }

    fun forget(uuid: UUID) {
        boards.remove(uuid)
    }
}
