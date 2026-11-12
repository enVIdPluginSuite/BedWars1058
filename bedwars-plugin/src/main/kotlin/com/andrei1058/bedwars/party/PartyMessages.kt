package com.andrei1058.bedwars.party

import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File

/**
 * Settings and texts of the built-in party system and of Play Again (`plugins/BedWars1058/party.yml`).
 * Missing keys are written back with their defaults, so the file always lists everything that can be changed.
 */
object PartyMessages {
    private const val LINE = "&9&m-----------------------------------------------------"

    private val defaults = linkedMapOf<String, Any>(
        "settings.max-size" to 12,
        "settings.invite-seconds" to 60,
        "settings.offline-minutes" to 5,
        "settings.command-aliases" to listOf("p"),

        "play-again.enabled" to true,
        "play-again.slot" to 4,
        "play-again.material" to "PAPER",
        "play-again.search-seconds" to 60,

        "messages.line" to LINE,
        "messages.help" to listOf(
            "{line}",
            "&6Party Commands:",
            "&e/party <player> &7- invite a player",
            "&e/party invite <player...> &7- invite players",
            "&e/party accept <player> &7- accept an invite",
            "&e/party deny <player> &7- decline an invite",
            "&e/party leave &7- leave your party",
            "&e/party list &7- show who is in the party",
            "&e/party chat [message] &7- party chat (or toggle it)  &8(/pc <message>)",
            "&e/party kick <player> &7- remove a player &8(moderator)",
            "&e/party warp &7- bring the party to you &8(moderator)",
            "&e/party mute &7- mute party chat &8(moderator)",
            "&e/party promote | demote <player> &7- change a member's role &8(leader)",
            "&e/party transfer <player> &7- hand the party over &8(leader)",
            "&e/party allinvite &7- let everyone invite &8(leader)",
            "&e/party kickoffline &7- remove offline players &8(moderator)",
            "&e/party disband &7- end the party &8(leader)",
            "{line}"
        ),
        "messages.only-players" to "&cOnly players can use party commands.",
        "messages.no-party" to "&cYou are not in a party.",
        "messages.not-leader" to "&cYou must be the party leader to do that.",
        "messages.not-moderator" to "&cYou must be a party moderator or the leader to do that.",
        "messages.player-offline" to "&cCouldn't find a player called &e{player}&c.",
        "messages.invite-self" to "&cYou can't invite yourself.",
        "messages.invite-not-allowed" to "&cYou are not allowed to invite players to this party.",
        "messages.invite-already" to "&cYou already invited &e{target}&c. Wait for it to expire.",
        "messages.target-in-your-party" to "&e{target}&c is already in your party.",
        "messages.target-in-party" to "&e{target}&c is already in a party.",
        "messages.party-full" to "&cThe party is full (&e{max}&c players).",
        "messages.invite-sent" to "{line}\n&e{inviter}&e invited &e{target}&e to the party! They have &c{seconds} seconds&e to accept.\n{line}",
        "messages.invite-received" to "{line}\n&e{inviter}&e has invited you to join their party!\n&eYou have &c{seconds} seconds&e to accept.",
        "messages.invite-click" to "&6&l[CLICK TO ACCEPT]",
        "messages.invite-end" to "{line}",
        "messages.invite-expired" to "&eThe party invite to &e{target}&e has expired.",
        "messages.invite-expired-target" to "&eThe party invite from &e{inviter}&e has expired.",
        "messages.party-empty-disbanded" to "&cThe party was disbanded because all invites expired and the party was empty.",
        "messages.accept-no-invite" to "&cYou don't have a pending invite from &e{player}&c.",
        "messages.accept-in-party" to "&cYou are already in a party. Use &e/party leave&c first.",
        "messages.accept-gone" to "&cThat party no longer exists.",
        "messages.deny-sent" to "&eYou declined the party invite from &e{player}&e.",
        "messages.deny-notice" to "&e{player}&e declined the party invite.",
        "messages.joined" to "&e{player}&e joined the party.",
        "messages.joined-you" to "&eYou joined &e{leader}&e's party.",
        "messages.left" to "&e{player}&e left the party.",
        "messages.left-you" to "&eYou left the party.",
        "messages.kicked" to "&e{player}&e was removed from the party by &e{by}&e.",
        "messages.kicked-you" to "&cYou were removed from the party.",
        "messages.kick-self" to "&cUse &e/party leave&c to leave the party.",
        "messages.not-in-your-party" to "&e{player}&c is not in your party.",
        "messages.cannot-kick" to "&cYou can't remove that player.",
        "messages.disbanded" to "&cThe party was disbanded.",
        "messages.disbanded-empty" to "&cThe party was disbanded because it ran out of members.",
        "messages.promoted-mod" to "&e{player}&e was promoted to party moderator by &e{by}&e.",
        "messages.promoted-leader" to "&e{by}&e handed the party to &e{player}&e. &e{by}&e is now a moderator.",
        "messages.demoted" to "&e{player}&e was demoted to party member by &e{by}&e.",
        "messages.already-leader" to "&cThat player already leads the party.",
        "messages.not-moderator-target" to "&e{player}&c is not a moderator.",
        "messages.leader-left" to "&e{player}&e is the new party leader.",
        "messages.list-header" to "{line}\n&6Party Members ({count}/{max})",
        "messages.list-leader" to "&eParty Leader: &f{players}",
        "messages.list-moderators" to "&eParty Moderators: &f{players}",
        "messages.list-members" to "&eParty Members: &f{players}",
        "messages.list-footer" to "{line}",
        "messages.status-offline" to "&c(offline)",
        "messages.chat-format" to "&9Party &8> &f{player}&f: {message}",
        "messages.chat-on" to "&aParty chat is now your default chat. Use &e/party chat&a to switch back.",
        "messages.chat-off" to "&eYou are back in normal chat.",
        "messages.chat-muted" to "&cThe party chat is muted.",
        "messages.muted" to "&e{by}&e muted the party chat.",
        "messages.unmuted" to "&e{by}&e unmuted the party chat.",
        "messages.allinvite-on" to "&e{by}&e allowed every member to invite players.",
        "messages.allinvite-off" to "&e{by}&e restricted invites to moderators and the leader.",
        "messages.warp-done" to "&aYou brought &e{count}&a party member(s) to you.",
        "messages.warp-you" to "&eYou were brought to &e{leader}&e.",
        "messages.warp-none" to "&cNobody could be brought to you right now.",
        "messages.warp-busy" to "&c{player}&c is in a running game and was not moved.",
        "messages.offline-notice" to "&e{player}&e disconnected. They have &c{minutes} minute(s)&e to rejoin.",
        "messages.offline-back" to "&e{player}&e rejoined the party.",
        "messages.offline-removed" to "&e{player}&e was removed from the party for being offline too long.",
        "messages.kickoffline-none" to "&cNobody in the party is offline.",
        "messages.kickoffline-done" to "&e{by}&e removed the offline players from the party.",

        "messages.play-again-name" to "&aPlay Again &7(Right Click)",
        "messages.play-again-lore" to listOf("&7Queue for another game", "&7with different players.", "", "&eRight click to play again"),
        "messages.play-again-searching" to "&eSearching for another game...",
        "messages.play-again-found" to "&aSending you to &e{arena}&a.",
        "messages.play-again-waiting" to "&eNo game is open right now, you will be placed in one as soon as it is.",
        "messages.play-again-timeout" to "&cNo game became available. Use &e/bw gui&c to pick one.",
        "messages.play-again-not-leader" to "&cOnly the party leader can queue the party for another game.",
        "messages.play-again-unavailable" to "&cPlay Again is not available here."
    )

    private lateinit var yml: YamlConfiguration

    fun load(plugin: Plugin) {
        val file = File(plugin.dataFolder, "party.yml")
        val loaded = YamlConfiguration.loadConfiguration(file)
        var changed = !file.isFile
        for ((key, value) in defaults) {
            if (!loaded.isSet(key)) {
                loaded.set(key, value)
                changed = true
            }
        }
        if (changed) runCatching { loaded.save(file) }
        yml = loaded
    }

    fun int(key: String): Int = if (::yml.isInitialized) yml.getInt(key, (defaults[key] as? Int) ?: 0) else (defaults[key] as? Int) ?: 0
    fun bool(key: String): Boolean = if (::yml.isInitialized) yml.getBoolean(key, (defaults[key] as? Boolean) ?: false) else (defaults[key] as? Boolean) ?: false
    fun string(key: String): String = (if (::yml.isInitialized) yml.getString(key) else null) ?: (defaults[key] as? String).orEmpty()

    @Suppress("UNCHECKED_CAST")
    fun stringList(key: String): List<String> =
        (if (::yml.isInitialized) yml.getStringList(key).takeIf { it.isNotEmpty() } else null) ?: (defaults[key] as? List<String>).orEmpty()

    private fun lineText() = string("messages.line")

    fun color(text: String): String = ChatColor.translateAlternateColorCodes('&', text)

    /** A message with its placeholders replaced and colours applied; may span several lines. */
    fun format(key: String, vararg values: Pair<String, String>): String {
        var text = string("messages.$key").replace("{line}", lineText())
        for ((name, value) in values) text = text.replace("{$name}", value)
        return color(text)
    }

    fun send(to: CommandSender, key: String, vararg values: Pair<String, String>) {
        val text = format(key, *values)
        if (text.isNotBlank()) text.split('\n').forEach { to.sendMessage(it) }
    }

    fun lines(key: String): List<String> = stringList("messages.$key").map { color(it.replace("{line}", lineText())) }
}
