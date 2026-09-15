package com.andrei1058.bedwars.teamselector.api

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.api.arena.team.ITeam
import org.bukkit.entity.Player

class TeamSelector : TeamSelectorAPI {

    override fun getSelectedTeam(player: Player): ITeam? {
        val arena = BedWars.getAPI().arenaUtil.getArenaByPlayer(player) ?: return null
        return arena.getTeam(player)
    }

    override fun getApiVersion(): Int = 2
}
