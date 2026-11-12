package com.andrei1058.bedwars.playagain

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.events.gameplay.GameStateChangeEvent
import com.andrei1058.bedwars.api.server.ServerType
import com.andrei1058.bedwars.arena.Arena
import com.andrei1058.bedwars.party.PartyMessages
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/**
 * "Play Again": when a game ends, every player gets a piece of paper. Right-clicking it (the party leader for a party)
 * queues them for another game of the same arena group, in a lobby with different players than the ones they just played
 * with. Only when no such lobby exists do they end up with the same people again. Nobody is queued automatically:
 * without the click, everyone simply goes back to the lobby.
 */
object PlayAgain : Listener {

    private class Pending(val leader: UUID, val members: List<UUID>, val avoid: Set<UUID>, val group: String, var secondsLeft: Int)

    /** Everybody who was in an arena when its game ended: the people the players should not meet again. */
    private val participants = ConcurrentHashMap<IArena, Set<UUID>>()
    private val pending = CopyOnWriteArrayList<Pending>()
    private val lastClick = ConcurrentHashMap<UUID, Long>()
    private lateinit var plugin: Plugin

    fun enable(owner: Plugin) {
        plugin = owner
        Bukkit.getPluginManager().registerEvents(this, owner)
        Bukkit.getScheduler().runTaskTimer(owner, Runnable { tickPending() }, 20L, 20L)
    }

    private fun enabled() = PartyMessages.bool("play-again.enabled") && BedWars.getServerType() != ServerType.BUNGEE

    // ---------------------------------------------------------------- the paper

    private fun material(): Material = Material.matchMaterial(PartyMessages.string("play-again.material")) ?: Material.PAPER

    private fun itemName() = PartyMessages.color(PartyMessages.string("messages.play-again-name"))

    private fun createItem(): ItemStack {
        val item = ItemStack(material())
        val meta = item.itemMeta
        meta.displayName = itemName()
        meta.lore = PartyMessages.lines("play-again-lore")
        item.itemMeta = meta
        return item
    }

    private fun isPlayAgain(item: ItemStack?): Boolean {
        if (item == null || item.type != material() || !item.hasItemMeta()) return false
        return item.itemMeta.displayName == itemName()
    }

    @EventHandler
    fun onState(e: GameStateChangeEvent) {
        val arena = e.arena
        when (e.newState) {
            GameState.waiting -> participants.remove(arena)
            GameState.restarting -> {
                if (!enabled()) return
                Bukkit.getScheduler().runTaskLater(plugin, Runnable { offer(arena) }, 5L)
            }
            else -> {}
        }
    }

    private fun offer(arena: IArena) {
        if (arena.status != GameState.restarting) return
        val everyone = (arena.players + arena.spectators).filter { it.isOnline }
        participants[arena] = everyone.map { it.uniqueId }.toSet()
        val slot = PartyMessages.int("play-again.slot").coerceIn(0, 8)
        for (player in everyone) player.inventory.setItem(slot, createItem())
    }

    @EventHandler
    fun onInteract(e: PlayerInteractEvent) {
        if (e.action != Action.RIGHT_CLICK_AIR && e.action != Action.RIGHT_CLICK_BLOCK) return
        if (!isPlayAgain(e.item)) return
        e.isCancelled = true
        // 1.9+ fires the event for both hands
        val now = System.currentTimeMillis()
        if (now - (lastClick.put(e.player.uniqueId, now) ?: 0L) < 300L) return
        click(e.player)
    }

    @EventHandler
    fun onInventory(e: InventoryClickEvent) {
        if (isPlayAgain(e.currentItem) || isPlayAgain(e.cursor)) e.isCancelled = true
    }

    @EventHandler
    fun onDrop(e: PlayerDropItemEvent) {
        if (isPlayAgain(e.itemDrop.itemStack)) e.isCancelled = true
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) {
        pending.removeAll { it.leader == e.player.uniqueId }
        lastClick.remove(e.player.uniqueId)
    }

    // ---------------------------------------------------------------- queueing

    private fun click(player: Player) {
        val arena = Arena.getArenaByPlayer(player)
        if (arena == null || arena.status != GameState.restarting || !enabled()) {
            return PartyMessages.send(player, "play-again-unavailable")
        }
        val party = BedWars.getParty()
        val inParty = party.hasParty(player)
        var group = listOf(player)
        if (inParty) {
            if (party.isOwner(player)) {
                group = party.getMembers(player).filter { Arena.getArenaByPlayer(it) === arena }.ifEmpty { listOf(player) }
            } else {
                val leader = party.getOwner(player)
                // the leader already left this game: let the member requeue on their own
                if (leader != null && Arena.getArenaByPlayer(leader) === arena) {
                    return PartyMessages.send(player, "play-again-not-leader")
                }
            }
        }
        val leader = if (inParty && party.isOwner(player)) player else group.first()
        if (pending.any { p -> p.members.any { m -> group.any { it.uniqueId == m } } }) return
        val request = Pending(
            leader.uniqueId,
            group.map { it.uniqueId },
            participants[arena] ?: (arena.players + arena.spectators).map { it.uniqueId }.toSet(),
            arena.group,
            PartyMessages.int("play-again.search-seconds").coerceAtLeast(5)
        )
        group.forEach { PartyMessages.send(it, "play-again-searching") }
        if (!place(request)) {
            pending += request
            group.forEach { PartyMessages.send(it, "play-again-waiting") }
        }
    }

    /**
     * Picks the lobby of the same group where the group meets the fewest people it just played with, preferring one that
     * already has players. When every open lobby already holds some of them, any open lobby will do.
     */
    private fun pick(request: Pending, size: Int): IArena? {
        val open = Arena.getArenas().filter { arena ->
            arena.group.equals(request.group, ignoreCase = true) && joinable(arena) && arena.maxPlayers - arena.players.size >= size
        }
        if (open.isEmpty()) return null
        fun overlap(a: IArena) = a.players.count { it.uniqueId in request.avoid }
        val fresh = open.filter { overlap(it) == 0 }
        if (fresh.isNotEmpty()) {
            val busiest = fresh.maxOf { it.players.size }
            return fresh.filter { it.players.size == busiest }.random(Random)
        }
        // every open lobby has someone from the last game: take the one with the fewest, then any of those
        val least = open.minOf { overlap(it) }
        return open.filter { overlap(it) == least }.random(Random)
    }

    private fun joinable(arena: IArena): Boolean = when (arena.status) {
        GameState.waiting -> true
        GameState.starting -> (arena.startingTask?.countdown ?: 0) > 1
        else -> false
    }

    /** Tries to move the group now. True when it left (or cannot be placed any more), false to keep waiting. */
    private fun place(request: Pending): Boolean {
        val online = request.members.mapNotNull { Bukkit.getPlayer(it) }.filter { it.isOnline }
        if (online.isEmpty()) return true
        val party = BedWars.getParty()
        val leader = Bukkit.getPlayer(request.leader)?.takeIf { it.isOnline } ?: online.first()
        // a party leader brings the whole party, so count everyone who would follow
        val size = if (party.hasParty(leader) && party.isOwner(leader)) {
            party.getMembers(leader).count { member ->
                val current = Arena.getArenaByPlayer(member)
                current == null || current.isSpectator(member) || online.contains(member)
            }.coerceAtLeast(online.size)
        } else online.size
        val target = pick(request, size) ?: return false

        online.forEach { detach(it) }
        val ok = if (party.hasParty(leader) && party.isOwner(leader)) {
            target.addPlayer(leader, false)
        } else {
            online.map { target.addPlayer(it, true) }.any { it }
        }
        if (!ok) return false
        online.forEach { PartyMessages.send(it, "play-again-found", "arena" to (target.displayName ?: target.arenaName)) }
        return true
    }

    private fun detach(player: Player) {
        val arena = Arena.getArenaByPlayer(player) ?: return
        if (arena.isSpectator(player)) arena.removeSpectator(player, false) else arena.removePlayer(player, false)
    }

    private fun tickPending() {
        for (request in pending) {
            request.secondsLeft--
            val timedOut = request.secondsLeft <= 0
            if (place(request)) {
                pending.remove(request)
            } else if (timedOut) {
                pending.remove(request)
                request.members.mapNotNull { Bukkit.getPlayer(it) }.forEach { PartyMessages.send(it, "play-again-timeout") }
            }
        }
    }
}
