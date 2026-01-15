package com.andrei1058.bedwars.support.version.v1_21_R1.sidebar

import com.andrei1058.spigot.sidebar.PlaceholderProvider
import com.andrei1058.spigot.sidebar.PlayerTab
import com.andrei1058.spigot.sidebar.SidebarLine
import com.andrei1058.spigot.sidebar.VersionedTabGroup
import com.andrei1058.spigot.sidebar.WrappedSidebar
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A scoreboard team mirrored on every receiver's board: players share prefix/suffix, collision and
 * name tag rules.
 */
internal class ModernTabGroup(
    private val sidebar: WrappedSidebar,
    private val groupIdentifier: String,
    private val prefix: SidebarLine?,
    private val suffix: SidebarLine?,
    private var pushingRule: PlayerTab.PushingRule,
    private var nameTagVisibility: PlayerTab.NameTagVisibility,
    private val placeholders: Collection<PlaceholderProvider>?
) : VersionedTabGroup {

    private val members = CopyOnWriteArrayList<Player>()
    private var subject: Player? = null

    /** Team names are limited to 16 characters. */
    private fun teamName(): String = groupIdentifier.take(MAX_TEAM_NAME_LENGTH)

    private fun render(line: SidebarLine?, receiver: Player): String {
        if (line == null) {
            return ""
        }
        return line.getTrimReplacePlaceholders(receiver, null, placeholders) ?: ""
    }

    /** Makes the receiver's board contain this team exactly as currently configured. */
    private fun applyTo(receiver: Player) {
        val board = BoardRegistry.get(receiver)
        val team = board.getTeam(teamName()) ?: board.registerNewTeam(teamName())

        val renderedPrefix = render(prefix, receiver)
        if (renderedPrefix != team.prefix) {
            team.prefix = renderedPrefix
        }
        val renderedSuffix = render(suffix, receiver)
        if (renderedSuffix != team.suffix) {
            team.suffix = renderedSuffix
        }

        val collisionRule = collisionStatus()
        if (team.getOption(Team.Option.COLLISION_RULE) != collisionRule) {
            team.setOption(Team.Option.COLLISION_RULE, collisionRule)
        }
        val nameTagRule = nameTagStatus()
        if (team.getOption(Team.Option.NAME_TAG_VISIBILITY) != nameTagRule) {
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, nameTagRule)
        }

        syncEntries(team)
    }

    /** Adds missing members to the team and removes entries that are not members anymore. */
    private fun syncEntries(team: Team) {
        val memberNames = members.map { it.name }

        for (memberName in memberNames) {
            if (!team.hasEntry(memberName)) {
                team.addEntry(memberName)
            }
        }
        for (entry in team.entries) {
            if (entry !in memberNames) {
                team.removeEntry(entry)
            }
        }
    }

    private fun collisionStatus(): Team.OptionStatus = when (pushingRule) {
        PlayerTab.PushingRule.ALWAYS -> Team.OptionStatus.ALWAYS
        PlayerTab.PushingRule.PUSH_OTHER_TEAMS -> Team.OptionStatus.FOR_OTHER_TEAMS
        PlayerTab.PushingRule.PUSH_OWN_TEAM -> Team.OptionStatus.FOR_OWN_TEAM
        else -> Team.OptionStatus.NEVER
    }

    private fun nameTagStatus(): Team.OptionStatus = when (nameTagVisibility) {
        PlayerTab.NameTagVisibility.NEVER -> Team.OptionStatus.NEVER
        PlayerTab.NameTagVisibility.HIDE_FOR_OTHER_TEAMS -> Team.OptionStatus.FOR_OWN_TEAM
        PlayerTab.NameTagVisibility.HIDE_FOR_OWN_TEAM -> Team.OptionStatus.FOR_OTHER_TEAMS
        else -> Team.OptionStatus.ALWAYS
    }

    override fun sendCreateToPlayer(player: Player) {
        applyTo(player)
    }

    override fun sendUserCreateToReceivers(player: Player) {
        for (receiver in sidebar.receivers) {
            applyTo(receiver)
        }
    }

    override fun sendUpdateToReceivers() {
        for (receiver in sidebar.receivers) {
            applyTo(receiver)
        }
    }

    override fun sendRemoveToReceivers() {
        for (receiver in sidebar.receivers) {
            removeFrom(receiver)
        }
    }

    private fun removeFrom(receiver: Player) {
        receiver.scoreboard.getTeam(teamName())?.unregister()
        BoardRegistry.releaseIfEmpty(receiver)
    }

    override fun refreshContent(): Boolean = true

    override fun add(player: Player) {
        if (!members.contains(player)) {
            members.add(player)
        }
        for (receiver in sidebar.receivers) {
            val team = receiver.scoreboard.getTeam(teamName())
            if (team != null && !team.hasEntry(player.name)) {
                team.addEntry(player.name)
            }
        }
    }

    override fun remove(player: Player) {
        members.remove(player)
        for (receiver in sidebar.receivers) {
            receiver.scoreboard.getTeam(teamName())?.removeEntry(player.name)
        }
        if (player.isOnline) {
            // the leaving player no longer receives this sidebar
            removeFrom(player)
        }
    }

    override fun setSubject(player: Player?) {
        this.subject = player
    }

    override fun getSubject(): Player? = subject

    override fun setPushingRule(rule: PlayerTab.PushingRule) {
        this.pushingRule = rule
        sendUpdateToReceivers()
    }

    override fun setNameTagVisibility(visibility: PlayerTab.NameTagVisibility) {
        this.nameTagVisibility = visibility
        sendUpdateToReceivers()
    }

    override fun getIdentifier(): String = groupIdentifier

    private companion object {
        const val MAX_TEAM_NAME_LENGTH = 16
    }
}
