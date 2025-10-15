package com.andrei1058.bedwars.support.version.modern.sidebar;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one private {@link Scoreboard} per player. Sidebar, health objective and tab teams are all
 * registered on the receiver's own board, so nothing leaks into other players' views or the main scoreboard.
 */
final class BoardRegistry {

    private static final Map<UUID, Scoreboard> BOARDS = new ConcurrentHashMap<>();

    private BoardRegistry() {
    }

    static Scoreboard get(Player player) {
        Scoreboard board = BOARDS.get(player.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            BOARDS.put(player.getUniqueId(), board);
        }
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
        return board;
    }

    /**
     * Hands the main scoreboard back once nothing of ours is left on the player's board.
     */
    static void releaseIfEmpty(Player player) {
        Scoreboard board = BOARDS.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        if (board.getObjectives().isEmpty() && board.getTeams().isEmpty()) {
            BOARDS.remove(player.getUniqueId());
            if (player.isOnline() && player.getScoreboard() == board) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
    }

    static void forget(UUID uuid) {
        BOARDS.remove(uuid);
    }
}
