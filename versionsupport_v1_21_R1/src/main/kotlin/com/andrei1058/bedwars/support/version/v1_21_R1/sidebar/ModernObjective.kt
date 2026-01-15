package com.andrei1058.bedwars.support.version.v1_21_R1.sidebar

import com.andrei1058.spigot.sidebar.SidebarLine
import com.andrei1058.spigot.sidebar.SidebarObjective
import com.andrei1058.spigot.sidebar.WrappedSidebar
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.RenderType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The sidebar title objective, or one of the health objectives (tab list / below the name tag).
 */
internal class ModernObjective(
    private val sidebar: WrappedSidebar,
    private val objectiveName: String,
    private val health: Boolean,
    private var titleLine: SidebarLine,
    @Suppress("unused") private val type: Int
) : SidebarObjective {

    private var renderedTitle = ""
    private val receivers = ConcurrentHashMap<UUID, Boolean>()

    fun isHealth(): Boolean = health

    override fun setTitle(title: SidebarLine) {
        this.titleLine = title
    }

    override fun getTitle(): SidebarLine = titleLine

    private fun render(player: Player): String {
        return titleLine.getTrimReplacePlaceholders(player, null, sidebar.placeholders) ?: ""
    }

    /** The objective as registered on the player's own board, or null if not created yet. */
    fun find(player: Player): Objective? = BoardRegistry.get(player).getObjective(objectiveName)

    override fun sendCreate(player: Player) {
        val board = BoardRegistry.get(player)
        board.getObjective(objectiveName)?.unregister()

        val objective: Objective
        if (health) {
            // scores are pushed by the plugin (sendScore), so the criteria must not be read-only like Criteria.HEALTH
            objective = board.registerNewObjective(objectiveName, Criteria.DUMMY, render(player), RenderType.HEARTS)
            // "health" is shown in the tab list, anything else (health2) under the name tag
            val isTabListObjective = objectiveName == "health"
            objective.displaySlot = if (isTabListObjective) DisplaySlot.PLAYER_LIST else DisplaySlot.BELOW_NAME
            if (!isTabListObjective) {
                objective.renderType = RenderType.INTEGER
            }
        } else {
            objective = board.registerNewObjective(objectiveName, Criteria.DUMMY, render(player))
            objective.displaySlot = DisplaySlot.SIDEBAR
        }
        receivers[player.uniqueId] = true
    }

    override fun sendUpdate() {
        for (player in sidebar.receivers) {
            find(player)?.displayName = render(player)
        }
    }

    override fun sendRemove(player: Player) {
        receivers.remove(player.uniqueId)
        player.scoreboard.getObjective(objectiveName)?.unregister()
        BoardRegistry.releaseIfEmpty(player)
    }

    override fun getName(): String = objectiveName

    override fun refreshTitle(): Boolean {
        val newTitle = titleLine.getTrimReplacePlaceholders(null, null, sidebar.placeholders) ?: ""
        val changed = newTitle != renderedTitle
        renderedTitle = newTitle
        return changed || titleLine.isHasPlaceholders
    }
}
