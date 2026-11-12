package com.andrei1058.bedwars.party

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.defaults.BukkitCommand
import org.bukkit.entity.Player

/** `/party` (alias `/p`): invite, accept, deny, leave, list, chat, kick, warp, mute, promote, demote, transfer, allinvite, kickoffline, disband. */
class PartyCommand(name: String) : BukkitCommand(name) {

    init {
        aliases = PartyMessages.stringList("settings.command-aliases")
        description = "Play with friends in a party."
        usageMessage = "/party help"
    }

    override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            PartyMessages.send(sender, "only-players")
            return true
        }
        if (args.isEmpty()) {
            PartyMessages.lines("help").forEach { player.sendMessage(it) }
            return true
        }
        val rest = args.drop(1)
        when (args[0].lowercase()) {
            "help", "?" -> PartyMessages.lines("help").forEach { player.sendMessage(it) }
            "invite", "add" -> invite(player, rest)
            "accept", "join" -> if (rest.isEmpty()) usage(player) else PartyService.accept(player, rest[0])
            "deny", "decline" -> if (rest.isEmpty()) usage(player) else PartyService.deny(player, rest[0])
            "leave", "quit" -> if (PartyService.partyOf(player) == null) PartyMessages.send(player, "no-party") else PartyService.leave(player)
            "list", "info" -> PartyService.list(player)
            "kick", "remove" -> if (rest.isEmpty()) usage(player) else PartyService.kick(player, rest[0])
            "promote" -> if (rest.isEmpty()) usage(player) else PartyService.promote(player, rest[0])
            "demote" -> if (rest.isEmpty()) usage(player) else PartyService.demote(player, rest[0])
            "transfer" -> if (rest.isEmpty()) usage(player) else PartyService.transfer(player, rest[0])
            "disband" -> PartyService.disband(player)
            "warp" -> PartyService.warp(player)
            "mute" -> PartyService.toggleMute(player)
            "allinvite" -> PartyService.toggleAllInvite(player)
            "kickoffline" -> PartyService.kickOffline(player)
            "chat", "c" -> if (rest.isEmpty()) PartyService.toggleChat(player) else PartyService.chat(player, rest.joinToString(" "))
            // /party <player>: invite shortcut, exactly like on Hypixel
            else -> invite(player, args.toList())
        }
        return true
    }

    private fun usage(player: Player) = PartyMessages.lines("help").forEach { player.sendMessage(it) }

    private fun invite(player: Player, names: List<String>) {
        if (names.isEmpty()) return usage(player)
        for (name in names.distinct()) {
            val target = Bukkit.getPlayerExact(name)
            if (target == null || !target.isOnline) {
                PartyMessages.send(player, "player-offline", "player" to name)
                continue
            }
            PartyService.invite(player, target)
        }
    }

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            val subs = listOf("invite", "accept", "deny", "leave", "list", "chat", "kick", "warp", "mute", "promote", "demote", "transfer", "allinvite", "kickoffline", "disband", "help")
            return subs.filter { it.startsWith(args[0], ignoreCase = true) } +
                Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size >= 2) {
            val mine = (sender as? Player)?.let { PartyService.partyOf(it) }
            val pool = when (args[0].lowercase()) {
                "kick", "remove", "promote", "demote", "transfer" -> mine?.online()?.map { it.name } ?: emptyList()
                else -> Bukkit.getOnlinePlayers().map { it.name }
            }
            return pool.filter { it.startsWith(args.last(), ignoreCase = true) }
        }
        return emptyList()
    }
}

/** `/pc <message>` (alias `/partychat`): talk in party chat without leaving normal chat. */
class PartyChatCommand(name: String) : BukkitCommand(name) {
    init {
        aliases = listOf("partychat")
        description = "Send a message to your party."
        usageMessage = "/pc <message>"
    }

    override fun execute(sender: CommandSender, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: return true
        if (args.isEmpty()) PartyService.toggleChat(player) else PartyService.chat(player, args.joinToString(" "))
        return true
    }
}
