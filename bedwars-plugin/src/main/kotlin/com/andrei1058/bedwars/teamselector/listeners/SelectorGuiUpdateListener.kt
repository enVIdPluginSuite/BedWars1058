package com.andrei1058.bedwars.teamselector.listeners

import com.andrei1058.bedwars.teamselector.api.events.TeamSelectorAbortEvent
import com.andrei1058.bedwars.teamselector.api.events.TeamSelectorChooseEvent
import com.andrei1058.bedwars.teamselector.teamselector.TeamSelectorGUI
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/**
 * Keeps the open GUIs in sync when somebody picks a team or leaves.
 */
class SelectorGuiUpdateListener : Listener {

    @EventHandler
    fun onTeamChosen(event: TeamSelectorChooseEvent) {
        if (event.isCancelled()) return
        TeamSelectorGUI.updateGUIs()
    }

    @EventHandler
    fun onAbort(event: TeamSelectorAbortEvent) {
        TeamSelectorGUI.updateGUIs()
    }
}
