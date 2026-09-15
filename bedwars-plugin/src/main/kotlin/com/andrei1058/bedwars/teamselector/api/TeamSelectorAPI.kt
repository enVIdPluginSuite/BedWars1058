package com.andrei1058.bedwars.teamselector.api

import com.andrei1058.bedwars.api.arena.team.ITeam
import org.bukkit.entity.Player

/**
 * Public API of the built-in team selector. Registered as a Bukkit service, so addons can fetch it with
 * `Bukkit.getServicesManager().getRegistration(TeamSelectorAPI::class.java)`.
 */
interface TeamSelectorAPI {

    /**
     * Get the team a player is assigned to in the arena he is playing in.
     */
    fun getSelectedTeam(player: Player): ITeam?

    /**
     * Get api version.
     */
    fun getApiVersion(): Int
}
