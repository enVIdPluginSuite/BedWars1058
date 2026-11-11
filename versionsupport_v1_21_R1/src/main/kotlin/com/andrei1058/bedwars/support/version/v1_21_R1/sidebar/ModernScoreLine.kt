package com.andrei1058.bedwars.support.version.v1_21_R1.sidebar

import com.andrei1058.spigot.sidebar.ScoreLine
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

    /** For pre-1.13 clients: the scoreboard entry that currently carries the row text. */
    private val sentEntry = ConcurrentHashMap<UUID, String>()

    /** Last right-side (score column) text pushed to each receiver. */
    private val sentRightText = ConcurrentHashMap<UUID, String>()

    /** Team and entry names stay unique per row, the color code is unique within a sidebar. */
    private fun teamName(): String = "bwl" + lineColor.replace("§", "")

    /** The invisible scoreboard entry this row is attached to. */
    private fun entryName(): String = lineColor + "§r"

    /**
     * The row text. A line can also carry text for the score column (the team status: tick, cross, "YOU"); that column
     * is drawn at the far right edge of the sidebar, so the text is appended right behind the left text instead, which
     * keeps it next to the team name.
     */
    private fun render(player: Player): String {
        val left = sidebarLine.getTrimReplacePlaceholders(player, null, sidebar.placeholders) ?: ""
        val right = sidebarLine.getTrimReplacePlaceholdersScore(player, null, sidebar.placeholders)
        return left + right
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
            val row = objective.getScore(sentEntry[player.uniqueId] ?: entryName())
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

        if (ClientVersions.isLegacy(player)) {
            applyLegacy(player, objective)
            return
        }
        // the player may have been a legacy client a moment ago (reconnect with another client): drop that row
        if (sentEntry.containsKey(player.uniqueId)) dropLegacy(player, board)

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

    /**
     * Pre-1.13 clients (Lunar 1.8.9 through ViaVersion) lose whatever does not fit the 16 characters of a team prefix,
     * and in practice the suffix does not arrive either, so a row is cut off after 16 characters. They get the whole
     * row (up to the 40 characters of a 1.8 score name) as the scoreboard entry itself instead, with no team at all.
     * The entry changes whenever the text does, so the previous one is removed.
     */
    private fun applyLegacy(player: Player, objective: Objective) {
        val text = render(player)
        val entry = legacyEntry(text)
        val previous = sentEntry.put(player.uniqueId, entry)
        sentText.put(player.uniqueId, text)
        val board = objective.scoreboard ?: return

        // a team left over from the modern layout would put its (empty) prefix around the entry
        board.getTeam(teamName())?.unregister()

        val row = objective.getScore(entry)
        if (!row.isScoreSet || row.score != scoreAmount) row.score = scoreAmount
        if (previous != null && previous != entry) board.resetScores(previous)
        if (paper && sentRightText.put(player.uniqueId, "") == null) PaperNumbers.apply(row, "")
    }

    private fun dropLegacy(player: Player, board: org.bukkit.scoreboard.Scoreboard) {
        sentEntry.remove(player.uniqueId)?.let { board.resetScores(it) }
        sentText.remove(player.uniqueId)
        sentRightText.remove(player.uniqueId)
    }

    /** The row text as a unique entry of at most [ENTRY_MAX] characters: the row's colour code at the end tells rows apart. */
    private fun legacyEntry(text: String): String = clampEntry(text, ENTRY_MAX - lineColor.length) + lineColor

    /** Pushes text, score and score column text, skipping whatever the receiver already has. */
    private fun applyContent(player: Player, team: Team, objective: Objective) {
        val text = render(player)
        val previousText = sentText.put(player.uniqueId, text)
        if (text != previousText) {
            // clients before 1.13 (reached through ViaVersion, e.g. Lunar 1.8.9) allow 16 characters per prefix and
            // suffix and cut the rest off, so long rows are split over both; newer clients show them as one text
            val (prefix, suffix) = splitRow(text)
            team.prefix = prefix
            team.suffix = suffix
        }

        val row = objective.getScore(entryName())
        if (row.score != scoreAmount || !row.isScoreSet) {
            row.score = scoreAmount
        }

        // the score digits stay hidden (blank number format, Paper only): the status is part of the text, see render
        if (paper && sentRightText.put(player.uniqueId, "") == null) {
            PaperNumbers.apply(row, "")
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
        sentEntry.remove(player.uniqueId)?.let { player.scoreboard.resetScores(it) }

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

    companion object {
        private const val HALF = 16

        /** The longest score name a 1.8 client accepts. */
        private const val ENTRY_MAX = 40

        /** [text] cut to at most [max] characters without leaving half a colour code at the end. */
        internal fun clampEntry(text: String, max: Int): String {
            if (text.length <= max) return text
            var cut = max
            if (text[cut - 1] == '§') cut--
            return text.substring(0, cut)
        }

        /**
         * Splits [text] into a team prefix of at most [HALF] characters and the rest as suffix, without cutting a
         * colour code in two. The suffix starts with the colours and formats that are active at the cut, because the
         * invisible scoreboard entry between the two resets them.
         */
        internal fun splitRow(text: String): Pair<String, String> {
            if (text.length <= HALF) return text to ""
            var cut = HALF
            if (text[cut - 1] == '§') cut-- // do not separate a code from its character
            val hex = text.lastIndexOf("§x", cut - 1)
            if (hex >= 0 && cut < hex + 14) cut = hex // a hex colour is 14 characters long
            if (cut <= 0) return text to ""
            val prefix = text.substring(0, cut)
            return prefix to activeCodes(prefix) + text.substring(cut)
        }

        /** The colour and formats still in effect at the end of [text]. */
        private fun activeCodes(text: String): String {
            var color = ""
            val formats = StringBuilder()
            var i = 0
            while (i < text.length - 1) {
                if (text[i] != '§') {
                    i++
                    continue
                }
                val c = text[i + 1].lowercaseChar()
                when {
                    c == 'x' && i + 14 <= text.length -> {
                        color = text.substring(i, i + 14)
                        formats.setLength(0)
                        i += 14
                        continue
                    }
                    c == 'r' -> {
                        color = ""
                        formats.setLength(0)
                    }
                    c in '0'..'9' || c in 'a'..'f' -> {
                        color = "§$c"
                        formats.setLength(0) // a colour code resets the formats
                    }
                    c in 'k'..'o' -> formats.append('§').append(c)
                }
                i += 2
            }
            return color + formats
        }
    }
}
