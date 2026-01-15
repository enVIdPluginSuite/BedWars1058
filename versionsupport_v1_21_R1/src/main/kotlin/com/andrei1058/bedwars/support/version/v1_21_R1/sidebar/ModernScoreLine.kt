package com.andrei1058.bedwars.support.version.v1_21_R1.sidebar

import com.andrei1058.spigot.sidebar.ScoreLine
import com.andrei1058.spigot.sidebar.ScoredLine
import com.andrei1058.spigot.sidebar.SidebarLine
import com.andrei1058.spigot.sidebar.WrappedSidebar
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Team
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * One sidebar row: a unique invisible entry (the color code) rendered through a team prefix, with the
 * score used for ordering.
 */
internal class ModernScoreLine(
    private val sidebar: WrappedSidebar,
    private var sidebarLine: SidebarLine,
    private var scoreAmount: Int,
    private val lineColor: String,
    private val paper: Boolean
) : ScoreLine {

    /** Last left-side text pushed to each receiver, to avoid redundant packets. */
    private val sentText = ConcurrentHashMap<UUID, String>()

    /** Last right-side (score column) text pushed to each receiver. */
    private val sentRightText = ConcurrentHashMap<UUID, String>()

    /** Team and entry names stay unique per row, the color code is unique within a sidebar. */
    private fun teamName(): String = "bwl" + lineColor.replace("§", "")

    /** The invisible scoreboard entry this row is attached to. */
    private fun entryName(): String = lineColor + "§r"

    private fun render(player: Player): String {
        return sidebarLine.getTrimReplacePlaceholders(player, null, sidebar.placeholders) ?: ""
    }

    private fun objectiveOf(player: Player): Objective? {
        return (sidebar.sidebarObjective as? ModernObjective)?.find(player)
    }

    override fun getLine(): SidebarLine = sidebarLine

    override fun setLine(line: SidebarLine) {
        this.sidebarLine = line
    }

    override fun getScoreAmount(): Int = scoreAmount

    override fun setScoreAmount(score: Int) {
        this.scoreAmount = score
        // the library shifts scores around without asking for an update (removing a line, for example)
        for (player in sidebar.receivers) {
            if (!sentText.containsKey(player.uniqueId)) {
                continue
            }
            val objective = objectiveOf(player) ?: continue
            val row = objective.getScore(entryName())
            if (!row.isScoreSet || row.score != score) {
                row.score = score
            }
        }
    }

    override fun sendCreateToAllReceivers() {
        for (player in sidebar.receivers) {
            sendCreate(player)
        }
    }

    override fun sendCreate(player: Player) {
        val objective = objectiveOf(player) ?: return
        val board = objective.scoreboard ?: return

        var team = board.getTeam(teamName())
        if (team == null) {
            team = board.registerNewTeam(teamName())
            // new board or new row: whatever was cached no longer exists client side
            sentText.remove(player.uniqueId)
            sentRightText.remove(player.uniqueId)
        }
        if (!team.hasEntry(entryName())) {
            team.addEntry(entryName())
        }
        applyContent(player, team, objective)
    }

    /** Pushes text, score and score column text, skipping whatever the receiver already has. */
    private fun applyContent(player: Player, team: Team, objective: Objective) {
        val text = render(player)
        val previousText = sentText.put(player.uniqueId, text)
        if (text != previousText) {
            team.prefix = text
        }

        val row = objective.getScore(entryName())
        if (row.score != scoreAmount || !row.isScoreSet) {
            row.score = scoreAmount
        }

        if (paper) {
            val rightText = (sidebarLine as? ScoredLine)?.score ?: ""
            val previousRightText = sentRightText.put(player.uniqueId, rightText)
            if (rightText != previousRightText) {
                PaperNumbers.apply(row, rightText)
            }
        }
    }

    override fun setContent(line: SidebarLine): Boolean {
        this.sidebarLine = line
        return refreshContent()
    }

    override fun sendUpdateToAllReceivers() {
        for (player in sidebar.receivers) {
            sendUpdate(player)
        }
    }

    override fun sendUpdate(player: Player) {
        sendCreate(player)
    }

    override fun sendRemoveToAllReceivers() {
        for (player in sidebar.receivers) {
            sendRemove(player)
        }
    }

    override fun sendRemove(player: Player) {
        sentText.remove(player.uniqueId)
        sentRightText.remove(player.uniqueId)

        val board = player.scoreboard
        val objective = board.getObjective(sidebar.sidebarObjective.name)
        objective?.scoreboard?.resetScores(entryName())
        board.getTeam(teamName())?.unregister()

        BoardRegistry.releaseIfEmpty(player)
    }

    override fun getColor(): String = lineColor

    override fun refreshContent(): Boolean = true

    override fun compareTo(other: ScoreLine): Int {
        // ascending: the library gives a new line the score of the first one (the lowest) and then shifts the others up
        return scoreAmount.compareTo(other.scoreAmount)
    }
}
