package com.andrei1058.bedwars.party

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.arena.Arena
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The built-in party system, modelled on Hypixel's: one leader, optional moderators, invites that expire, party chat,
 * warping, and members who can disconnect and come back. A party outlives games: leaving an arena never disbands it.
 */
object PartyService {

    class Party(var leader: UUID) {
        val members: MutableSet<UUID> = LinkedHashSet<UUID>().also { it.add(leader) }
        val moderators: MutableSet<UUID> = LinkedHashSet()
        var allInvite = false
        var muted = false
        val offlineSince: MutableMap<UUID, Long> = HashMap()

        fun online(): List<Player> = members.mapNotNull { Bukkit.getPlayer(it) }.filter { it.isOnline }
    }

    private class Invite(val party: Party, val inviter: UUID, val inviterName: String, val expires: Long)

    private val byPlayer = ConcurrentHashMap<UUID, Party>()
    private val parties = java.util.concurrent.CopyOnWriteArrayList<Party>()
    private val invites = ConcurrentHashMap<UUID, MutableList<Invite>>()
    private val partyChat: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private var ticker: BukkitTask? = null

    fun start(plugin: Plugin) {
        ticker?.cancel()
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 20L, 20L)
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        byPlayer.clear(); parties.clear(); invites.clear(); partyChat.clear()
    }

    // ---------------------------------------------------------------- lookups

    fun partyOf(p: Player): Party? = byPlayer[p.uniqueId]
    fun isLeader(p: Player): Boolean = partyOf(p)?.leader == p.uniqueId
    fun isModerator(p: Player): Boolean = partyOf(p)?.let { it.leader == p.uniqueId || p.uniqueId in it.moderators } == true

    private val maxSize get() = PartyMessages.int("settings.max-size").coerceAtLeast(2)
    private val inviteMillis get() = PartyMessages.int("settings.invite-seconds").coerceAtLeast(5) * 1000L
    private val offlineMillis get() = PartyMessages.int("settings.offline-minutes").coerceAtLeast(1) * 60_000L

    private fun nameOf(id: UUID): String = Bukkit.getPlayer(id)?.name ?: Bukkit.getOfflinePlayer(id).name ?: "?"
    private fun tell(party: Party, key: String, vararg values: Pair<String, String>) = party.online().forEach { PartyMessages.send(it, key, *values) }

    // ---------------------------------------------------------------- creating / joining

    /** Used by [com.andrei1058.bedwars.support.party.Internal]: a ready-made party with [members] in it. */
    fun create(leader: Player, vararg members: Player): Party {
        partyOf(leader)?.let { return it }
        val party = Party(leader.uniqueId)
        parties += party
        byPlayer[leader.uniqueId] = party
        for (member in members) if (member !== leader) join(party, member, silent = false)
        return party
    }

    fun join(party: Party, player: Player, silent: Boolean = false) {
        if (partyOf(player) != null) return
        invites.remove(player.uniqueId)
        party.members += player.uniqueId
        byPlayer[player.uniqueId] = party
        if (!silent) {
            tell(party, "joined", "player" to player.name)
        }
    }

    fun invite(inviter: Player, target: Player) {
        if (target.uniqueId == inviter.uniqueId) return PartyMessages.send(inviter, "invite-self")
        val existing = partyOf(inviter)
        if (existing != null && !(isModerator(inviter) || existing.allInvite)) return PartyMessages.send(inviter, "invite-not-allowed")
        val targetParty = partyOf(target)
        if (targetParty != null) {
            return PartyMessages.send(inviter, if (targetParty === existing) "target-in-your-party" else "target-in-party", "target" to target.name)
        }
        if (existing != null) {
            val pending = invites.values.sumOf { list -> list.count { it.party === existing } }
            if (existing.members.size + pending >= maxSize) return PartyMessages.send(inviter, "party-full", "max" to maxSize.toString())
            if (invites[target.uniqueId]?.any { it.party === existing } == true) {
                return PartyMessages.send(inviter, "invite-already", "target" to target.name)
            }
        }
        val party = existing ?: create(inviter)
        val seconds = (inviteMillis / 1000).toString()
        invites.computeIfAbsent(target.uniqueId) { java.util.concurrent.CopyOnWriteArrayList() }
            .add(Invite(party, inviter.uniqueId, inviter.name, System.currentTimeMillis() + inviteMillis))

        tell(party, "invite-sent", "inviter" to inviter.name, "target" to target.name, "seconds" to seconds)
        PartyMessages.send(target, "invite-received", "inviter" to inviter.name, "seconds" to seconds)
        val click = TextComponent(PartyMessages.color(PartyMessages.string("messages.invite-click")))
        click.clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party accept ${inviter.name}")
        target.spigot().sendMessage(click)
        PartyMessages.send(target, "invite-end")
    }

    fun accept(player: Player, inviterName: String) {
        if (partyOf(player) != null) return PartyMessages.send(player, "accept-in-party")
        val list = invites[player.uniqueId].orEmpty()
        val invite = list.firstOrNull { it.inviterName.equals(inviterName, ignoreCase = true) && it.expires > System.currentTimeMillis() }
            ?: return PartyMessages.send(player, "accept-no-invite", "player" to inviterName)
        invites.remove(player.uniqueId)
        val party = invite.party
        if (party !in parties) return PartyMessages.send(player, "accept-gone")
        if (party.members.size >= maxSize) return PartyMessages.send(player, "party-full", "max" to maxSize.toString())
        PartyMessages.send(player, "joined-you", "leader" to nameOf(party.leader))
        join(party, player)
    }

    fun deny(player: Player, inviterName: String) {
        val list = invites[player.uniqueId]
        val invite = list?.firstOrNull { it.inviterName.equals(inviterName, ignoreCase = true) }
            ?: return PartyMessages.send(player, "accept-no-invite", "player" to inviterName)
        list.remove(invite)
        PartyMessages.send(player, "deny-sent", "player" to invite.inviterName)
        tell(invite.party, "deny-notice", "player" to player.name)
        disbandIfEmpty(invite.party, "party-empty-disbanded")
    }

    // ---------------------------------------------------------------- leaving / removing

    fun leave(player: Player, announce: Boolean = true) {
        val party = partyOf(player) ?: return
        PartyMessages.send(player, "left-you")
        remove(party, player.uniqueId, "left", player.name, announce)
    }

    /** Takes [id] out of [party]. If the leader goes, the next moderator (or member) takes over. */
    private fun remove(party: Party, id: UUID, messageKey: String, name: String, announce: Boolean = true) {
        party.members -= id
        party.moderators -= id
        party.offlineSince.remove(id)
        byPlayer.remove(id)
        partyChat.remove(id)
        if (announce) tell(party, messageKey, "player" to name, "by" to name)
        if (party.members.size <= 1) {
            disbandInternal(party, "disbanded-empty")
            return
        }
        if (party.leader == id) {
            val next = party.moderators.firstOrNull() ?: party.members.first()
            party.moderators -= next
            party.leader = next
            tell(party, "leader-left", "player" to nameOf(next))
        }
    }

    fun kick(by: Player, targetName: String) {
        val party = partyOf(by) ?: return PartyMessages.send(by, "no-party")
        if (!isModerator(by)) return PartyMessages.send(by, "not-moderator")
        val target = party.members.firstOrNull { nameOf(it).equals(targetName, ignoreCase = true) }
            ?: return PartyMessages.send(by, "not-in-your-party", "player" to targetName)
        if (target == by.uniqueId) return PartyMessages.send(by, "kick-self")
        // moderators can remove members, only the leader can remove moderators
        if (target == party.leader || (target in party.moderators && !isLeader(by))) return PartyMessages.send(by, "cannot-kick")
        val name = nameOf(target)
        Bukkit.getPlayer(target)?.let { PartyMessages.send(it, "kicked-you") }
        tell(party, "kicked", "player" to name, "by" to by.name)
        remove(party, target, "kicked", name, announce = false)
    }

    fun kickOffline(by: Player) {
        val party = partyOf(by) ?: return PartyMessages.send(by, "no-party")
        if (!isModerator(by)) return PartyMessages.send(by, "not-moderator")
        val offline = party.members.filter { Bukkit.getPlayer(it)?.isOnline != true }
        if (offline.isEmpty()) return PartyMessages.send(by, "kickoffline-none")
        tell(party, "kickoffline-done", "by" to by.name)
        offline.forEach { if (it in party.members) remove(party, it, "left", nameOf(it), announce = false) }
    }

    fun disband(by: Player) {
        val party = partyOf(by) ?: return PartyMessages.send(by, "no-party")
        if (!isLeader(by)) return PartyMessages.send(by, "not-leader")
        disbandInternal(party, "disbanded")
    }

    /** Disbands without a permission check (API / game logic). */
    fun disbandParty(party: Party) = disbandInternal(party, "disbanded")

    private fun disbandInternal(party: Party, key: String) {
        tell(party, key)
        parties -= party
        party.members.forEach { byPlayer.remove(it); partyChat.remove(it) }
        invites.values.forEach { list -> list.removeAll { it.party === party } }
        party.members.clear()
    }

    private fun disbandIfEmpty(party: Party, key: String) {
        if (party.members.size == 1 && invites.values.none { list -> list.any { it.party === party } }) disbandInternal(party, key)
    }

    // ---------------------------------------------------------------- roles

    fun promote(by: Player, targetName: String) {
        val party = partyOf(by) ?: return PartyMessages.send(by, "no-party")
        if (!isLeader(by)) return PartyMessages.send(by, "not-leader")
        val target = party.members.firstOrNull { nameOf(it).equals(targetName, ignoreCase = true) }
            ?: return PartyMessages.send(by, "not-in-your-party", "player" to targetName)
        when {
            target == by.uniqueId -> PartyMessages.send(by, "already-leader")
            target in party.moderators -> transferTo(party, target)
            else -> {
                party.moderators += target
                tell(party, "promoted-mod", "player" to nameOf(target), "by" to by.name)
            }
        }
    }

    fun demote(by: Player, targetName: String) {
        val party = partyOf(by) ?: return PartyMessages.send(by, "no-party")
        if (!isLeader(by)) return PartyMessages.send(by, "not-leader")
        val target = party.moderators.firstOrNull { nameOf(it).equals(targetName, ignoreCase = true) }
            ?: return PartyMessages.send(by, "not-moderator-target", "player" to targetName)
        party.moderators -= target
        tell(party, "demoted", "player" to nameOf(target), "by" to by.name)
    }

    fun transfer(by: Player, targetName: String) {
        val party = partyOf(by) ?: return PartyMessages.send(by, "no-party")
        if (!isLeader(by)) return PartyMessages.send(by, "not-leader")
        val target = party.members.firstOrNull { nameOf(it).equals(targetName, ignoreCase = true) }
            ?: return PartyMessages.send(by, "not-in-your-party", "player" to targetName)
        if (target == by.uniqueId) return PartyMessages.send(by, "already-leader")
        transferTo(party, target)
    }

    private fun transferTo(party: Party, target: UUID) {
        val old = party.leader
        party.moderators -= target
        party.moderators += old
        party.leader = target
        tell(party, "promoted-leader", "player" to nameOf(target), "by" to nameOf(old))
    }

    fun toggleMute(by: Player) {
        val party = partyOf(by) ?: return PartyMessages.send(by, "no-party")
        if (!isModerator(by)) return PartyMessages.send(by, "not-moderator")
        party.muted = !party.muted
        tell(party, if (party.muted) "muted" else "unmuted", "by" to by.name)
    }

    fun toggleAllInvite(by: Player) {
        val party = partyOf(by) ?: return PartyMessages.send(by, "no-party")
        if (!isLeader(by)) return PartyMessages.send(by, "not-leader")
        party.allInvite = !party.allInvite
        tell(party, if (party.allInvite) "allinvite-on" else "allinvite-off", "by" to by.name)
    }

    // ---------------------------------------------------------------- chat / list / warp

    fun chat(sender: Player, message: String) {
        val party = partyOf(sender) ?: return PartyMessages.send(sender, "no-party")
        if (party.muted && !isModerator(sender)) return PartyMessages.send(sender, "chat-muted")
        val text = PartyMessages.format("chat-format", "player" to sender.displayName, "message" to message)
        party.online().forEach { it.sendMessage(text) }
        Bukkit.getConsoleSender().sendMessage(text)
    }

    fun toggleChat(player: Player) {
        if (partyOf(player) == null) return PartyMessages.send(player, "no-party")
        if (partyChat.add(player.uniqueId)) PartyMessages.send(player, "chat-on")
        else {
            partyChat.remove(player.uniqueId)
            PartyMessages.send(player, "chat-off")
        }
    }

    fun usesPartyChat(player: Player): Boolean = player.uniqueId in partyChat && partyOf(player) != null

    fun list(player: Player) {
        val party = partyOf(player) ?: return PartyMessages.send(player, "no-party")
        fun names(ids: Collection<UUID>) = ids.joinToString("&7, &f") { id ->
            nameOf(id) + if (Bukkit.getPlayer(id)?.isOnline == true) "" else " " + PartyMessages.string("messages.status-offline")
        }
        PartyMessages.send(player, "list-header", "count" to party.members.size.toString(), "max" to maxSize.toString())
        PartyMessages.send(player, "list-leader", "players" to names(listOf(party.leader)))
        if (party.moderators.isNotEmpty()) PartyMessages.send(player, "list-moderators", "players" to names(party.moderators))
        val rest = party.members.filter { it != party.leader && it !in party.moderators }
        if (rest.isNotEmpty()) PartyMessages.send(player, "list-members", "players" to names(rest))
        PartyMessages.send(player, "list-footer")
    }

    /**
     * Brings the party to the leader/moderator: into the arena they are waiting in, or to their position in the lobby.
     * Players who are in a running game are left alone.
     */
    fun warp(by: Player) {
        val party = partyOf(by) ?: return PartyMessages.send(by, "no-party")
        if (!isModerator(by)) return PartyMessages.send(by, "not-moderator")
        val arena = Arena.getArenaByPlayer(by)
        var moved = 0
        for (member in party.online()) {
            if (member === by) continue
            val current = Arena.getArenaByPlayer(member)
            if (current != null && current === arena) continue
            if (current != null && !current.isSpectator(member) && current.status == GameState.playing) {
                PartyMessages.send(by, "warp-busy", "player" to member.name)
                continue
            }
            if (current != null) {
                if (current.isSpectator(member)) current.removeSpectator(member, false) else current.removePlayer(member, false)
            }
            val ok = if (arena != null) arena.addPlayer(member, true) else member.teleport(by.location)
            if (ok) {
                moved++
                PartyMessages.send(member, "warp-you", "leader" to by.name)
            }
        }
        if (moved == 0) PartyMessages.send(by, "warp-none") else PartyMessages.send(by, "warp-done", "count" to moved.toString())
    }

    // ---------------------------------------------------------------- connection handling

    fun onQuit(player: Player) {
        invites.remove(player.uniqueId)
        partyChat.remove(player.uniqueId)
        val party = partyOf(player) ?: return
        party.offlineSince[player.uniqueId] = System.currentTimeMillis()
        tell(party, "offline-notice", "player" to player.name, "minutes" to (offlineMillis / 60_000).toString())
    }

    fun onJoin(player: Player) {
        val party = partyOf(player) ?: return
        if (party.offlineSince.remove(player.uniqueId) != null) {
            tell(party, "offline-back", "player" to player.name)
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        // expired invites
        for ((invitee, list) in invites) {
            val expired = list.filter { it.expires <= now }
            if (expired.isEmpty()) continue
            list.removeAll(expired.toSet())
            if (list.isEmpty()) invites.remove(invitee)
            for (invite in expired) {
                if (invite.party !in parties) continue
                val name = nameOf(invitee)
                tell(invite.party, "invite-expired", "target" to name)
                Bukkit.getPlayer(invitee)?.let { PartyMessages.send(it, "invite-expired-target", "inviter" to invite.inviterName) }
                disbandIfEmpty(invite.party, "party-empty-disbanded")
            }
        }
        // players who stayed offline too long
        for (party in parties.toList()) {
            val gone = party.offlineSince.filterValues { now - it >= offlineMillis }.keys
            for (id in gone) {
                if (id !in party.members) continue
                val name = nameOf(id)
                remove(party, id, "offline-removed", name)
                if (party !in parties) break
            }
        }
    }

    /** Parties and their members, for the plugin API. */
    fun allParties(): List<Party> = parties.toList()
}
