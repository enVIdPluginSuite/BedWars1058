package com.andrei1058.bedwars.teamselector

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.teamselector.api.TeamSelector
import com.andrei1058.bedwars.teamselector.api.TeamSelectorAPI
import com.andrei1058.bedwars.teamselector.configuration.Config
import com.andrei1058.bedwars.teamselector.configuration.Messages
import com.andrei1058.bedwars.teamselector.listeners.ArenaListener
import com.andrei1058.bedwars.teamselector.listeners.InventoryListener
import com.andrei1058.bedwars.teamselector.listeners.PlayerInteractListener
import com.andrei1058.bedwars.teamselector.listeners.SelectorGuiUpdateListener
import org.bukkit.Bukkit
import org.bukkit.plugin.ServicePriority

/**
 * Team selector: the item players get in the lobby to pick the team they want to play in.
 * This used to be the separate BedWars1058-TeamSelector addon, now it is part of the plugin.
 */
object TeamSelectorModule {

    private const val STANDALONE_ADDON_NAME = "BedWars1058-TeamSelector"

    /**
     * Must be called once the languages are loaded.
     */
    @JvmStatic
    fun enable() {
        val plugin = BedWars.plugin

        // two selectors would give two items and two GUIs: the old addon keeps working until its jar is removed
        if (Bukkit.getPluginManager().getPlugin(STANDALONE_ADDON_NAME) != null) {
            plugin.logger.warning(
                "$STANDALONE_ADDON_NAME is installed, but the team selector is now built into BedWars1058. " +
                        "The built-in selector stays off until you delete the addon jar. Your settings are kept."
            )
            return
        }

        Config.addDefaultConfig()
        Messages.setupMessages()

        Bukkit.getServicesManager().register(TeamSelectorAPI::class.java, TeamSelector(), plugin, ServicePriority.Normal)

        BedWars.registerEvents(
            ArenaListener(), InventoryListener(), PlayerInteractListener(), SelectorGuiUpdateListener()
        )
    }
}
