package com.andrei1058.bedwars.party

import com.andrei1058.bedwars.playagain.PlayAgain
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

/** Starts the party system (settings, service, listeners) and Play Again. */
object PartyModule {
    fun enable(plugin: Plugin) {
        PartyMessages.load(plugin)
        PartyService.start(plugin)
        Bukkit.getPluginManager().registerEvents(PartyListener(), plugin)
        PlayAgain.enable(plugin)
    }

    fun disable() = PartyService.stop()
}
