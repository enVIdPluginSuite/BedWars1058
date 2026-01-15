package com.andrei1058.bedwars.support.version.v1_21_R1.sidebar

import com.andrei1058.bedwars.support.version.v1_18_R2.VersionCompat
import com.andrei1058.spigot.sidebar.PlaceholderProvider
import com.andrei1058.spigot.sidebar.PlayerTab
import com.andrei1058.spigot.sidebar.ScoreLine
import com.andrei1058.spigot.sidebar.Sidebar
import com.andrei1058.spigot.sidebar.SidebarLine
import com.andrei1058.spigot.sidebar.SidebarObjective
import com.andrei1058.spigot.sidebar.SidebarProvider
import com.andrei1058.spigot.sidebar.VersionedTabGroup
import com.andrei1058.spigot.sidebar.WrappedSidebar
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin

/**
 * Sidebar implementation for 1.21+ and 26.x built purely on the Bukkit scoreboard API, so it does not
 * depend on packet classes that change on every release. Plugged into the sidebar library by
 * `SidebarBootstrap` when the library has no provider for the running server.
 */
open class ModernSidebarProvider(plugin: Plugin) : SidebarProvider() {

    private val paper = VersionCompat.isPaper()

    init {
        plugin.server.pluginManager.registerEvents(object : Listener {
            @EventHandler
            fun onQuit(event: PlayerQuitEvent) {
                BoardRegistry.forget(event.player.uniqueId)
            }
        }, plugin)
    }

    override fun createSidebar(
        title: SidebarLine, lines: Collection<SidebarLine>, placeholders: Collection<PlaceholderProvider>
    ): Sidebar = WrappedSidebar(title, lines, placeholders)

    override fun createObjective(
        sidebar: WrappedSidebar, name: String, health: Boolean, title: SidebarLine, type: Int
    ): SidebarObjective = ModernObjective(sidebar, name, health, title, type)

    override fun createScoreLine(sidebar: WrappedSidebar, line: SidebarLine, score: Int, color: String): ScoreLine =
        ModernScoreLine(sidebar, line, score, color, paper)

    override fun sendScore(sidebar: WrappedSidebar, playerName: String, score: Int) {
        val healthObjective = sidebar.healthObjective as? ModernObjective ?: return
        for (receiver in sidebar.receivers) {
            healthObjective.find(receiver)?.getScore(playerName)?.score = score
        }
    }

    override fun createPlayerTab(
        sidebar: WrappedSidebar, identifier: String, prefix: SidebarLine?, suffix: SidebarLine?,
        pushingRule: PlayerTab.PushingRule, nameTagVisibility: PlayerTab.NameTagVisibility,
        placeholders: Collection<PlaceholderProvider>?
    ): VersionedTabGroup =
        ModernTabGroup(sidebar, identifier, prefix, suffix, pushingRule, nameTagVisibility, placeholders)

    override fun sendHeaderFooter(player: Player, header: String?, footer: String?) {
        player.setPlayerListHeaderFooter(header ?: "", footer ?: "")
    }
}
