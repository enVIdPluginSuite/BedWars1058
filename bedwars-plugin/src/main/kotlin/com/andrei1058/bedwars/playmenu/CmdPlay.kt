package com.andrei1058.bedwars.playmenu

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.api.command.ParentCommand
import com.andrei1058.bedwars.api.command.SubCommand
import com.andrei1058.bedwars.api.configuration.ConfigPath
import com.andrei1058.bedwars.api.language.Language
import com.andrei1058.bedwars.api.language.Messages
import com.andrei1058.bedwars.arena.Arena
import com.andrei1058.bedwars.arena.SetupSession
import com.andrei1058.bedwars.commands.bedwars.MainCommand
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player

/**
 * `/bw play [arenaGroup]`: opens the play menu. Without a group the menu works on all the arenas.
 */
class CmdPlay(parent: ParentCommand, name: String) : SubCommand(parent, name) {

    init {
        setPriority(18)
        showInList(false)
        setDisplayInfo(
            MainCommand.createTC(
                "§6 ▪ §7/${MainCommand.getInstance().name} $subCommandName §e[arenaGroup]",
                "/${parent.name} $subCommandName",
                "§fOpens the play menu: join a random arena, pick an arena or reconnect."
            )
        )
    }

    override fun execute(args: Array<String>, s: CommandSender): Boolean {
        if (s is ConsoleCommandSender) return false
        val player = s as Player
        if (Arena.getArenaByPlayer(player) != null) return false

        val group = if (args.isNotEmpty()) args[0] else PlayMenu.DEFAULT_GROUP
        if (!isKnownGroup(group)) {
            player.sendMessage(Language.getMsg(player, Messages.COMMAND_JOIN_GROUP_OR_ARENA_NOT_FOUND).replace("{name}", group))
            return true
        }

        PlayMenu.open(player, group)
        return true
    }

    /**
     * Groups can be combined with a plus, the same way /bw join accepts them.
     */
    private fun isKnownGroup(group: String): Boolean =
        group.split("+").all { it.equals(PlayMenu.DEFAULT_GROUP, ignoreCase = true) || MainCommand.isArenaGroup(it) }

    override fun getTabComplete(): List<String> =
        BedWars.config.yml.getStringList(ConfigPath.GENERAL_CONFIGURATION_ARENA_GROUPS)

    override fun canSee(s: CommandSender, api: com.andrei1058.bedwars.api.BedWars): Boolean {
        if (s is ConsoleCommandSender) return false

        val player = s as Player
        if (Arena.isInArena(player)) return false
        if (SetupSession.isInSetupSession(player.uniqueId)) return false
        return hasPermission(s)
    }
}
