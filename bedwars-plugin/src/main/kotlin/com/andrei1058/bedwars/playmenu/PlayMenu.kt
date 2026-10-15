package com.andrei1058.bedwars.playmenu

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.api.language.Language
import com.andrei1058.bedwars.api.language.Messages
import com.andrei1058.bedwars.arena.Arena
import com.andrei1058.bedwars.arena.ArenaGUI
import com.andrei1058.bedwars.arena.ReJoin
import com.andrei1058.bedwars.commands.rejoin.RejoinCommand
import com.andrei1058.bedwars.configuration.Sounds
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/**
 * The "Play Bed Wars" menu: random arena, arena selector, close and reconnect buttons.
 * It works on one arena group at a time, [DEFAULT_GROUP] meaning all the arenas.
 */
object PlayMenu {

    const val DEFAULT_GROUP = "default"

    private const val MENU_SIZE = 36
    private const val RANDOM_ARENA_SLOT = 12
    private const val ARENA_SELECTOR_SLOT = 14
    private const val CLOSE_SLOT = 31
    private const val RECONNECT_SLOT = 35

    /** The inventory holder that tells the click listener this inventory is the play menu. */
    class PlayMenuHolder(val group: String) : InventoryHolder {
        // the inventory is created together with the holder, see open()
        override fun getInventory(): Inventory? = null
    }

    /**
     * Open the menu for a player.
     * @param group the arena group the buttons act on.
     */
    fun open(player: Player, group: String) {
        val inventory = Bukkit.createInventory(PlayMenuHolder(group), MENU_SIZE, Language.getMsg(player, PlayMenuMessages.TITLE))

        val blankPane = createBlankPane()
        for (slot in 0 until MENU_SIZE) {
            inventory.setItem(slot, blankPane)
        }

        inventory.setItem(RANDOM_ARENA_SLOT, createRandomArenaButton(player, group))
        inventory.setItem(ARENA_SELECTOR_SLOT, createArenaSelectorButton(player))
        inventory.setItem(CLOSE_SLOT, createCloseButton(player))
        inventory.setItem(RECONNECT_SLOT, createReconnectButton(player))

        player.openInventory(inventory)
        Sounds.playSound("arena-selector-open", player)
    }

    // ---------------------------------------------------------------------------------------------
    // clicks
    // ---------------------------------------------------------------------------------------------

    /**
     * Run what the button in [slot] does. Slots without a button do nothing.
     */
    fun onButtonClick(player: Player, group: String, slot: Int) {
        when (slot) {
            RANDOM_ARENA_SLOT -> joinRandomArena(player, group)
            ARENA_SELECTOR_SLOT -> openArenaSelector(player, group)
            CLOSE_SLOT -> player.closeInventory()
            RECONNECT_SLOT -> reconnect(player)
        }
    }

    private fun joinRandomArena(player: Player, group: String) {
        if (group.equals(DEFAULT_GROUP, ignoreCase = true)) {
            Arena.joinRandomArena(player)
        } else {
            Arena.joinRandomFromGroup(player, group)
        }

        // joining is the only way to end up in an arena from here, so this tells whether it worked
        if (Arena.getArenaByPlayer(player) != null) {
            Sounds.playSound("join-allowed", player)
            player.closeInventory()
        } else {
            Sounds.playSound("join-denied", player)
            player.sendMessage(Language.getMsg(player, Messages.COMMAND_JOIN_NO_EMPTY_FOUND))
        }
    }

    /**
     * Swap to the regular arena list. Done on the next tick, because inventories should not be replaced while
     * the click that triggered it is still being processed.
     */
    private fun openArenaSelector(player: Player, group: String) {
        player.closeInventory()
        Bukkit.getScheduler().runTask(BedWars.plugin, Runnable {
            if (player.isOnline && Arena.getArenaByPlayer(player) == null) {
                ArenaGUI.openGui(player, group)
            }
        })
    }

    private fun reconnect(player: Player) {
        player.closeInventory()
        RejoinCommand.attemptRejoin(player)
    }

    // ---------------------------------------------------------------------------------------------
    // items
    // ---------------------------------------------------------------------------------------------

    private fun createRandomArenaButton(player: Player, group: String): ItemStack {
        val isDefaultGroup = group.equals(DEFAULT_GROUP, ignoreCase = true)
        // the group line is only meaningful when the menu is limited to a group
        val lore = Language.getList(player, PlayMenuMessages.RANDOM_LORE)
            .filterNot { isDefaultGroup && it.contains("{group}") }
            .map { it.replace("{group}", group) }
            .let { if (isDefaultGroup) it.dropLastWhile { line -> line.isBlank() } else it }

        return createButton(
            BedWars.getForCurrentVersion("BED", "BED", "RED_BED"),
            0,
            Language.getMsg(player, PlayMenuMessages.RANDOM_NAME),
            lore
        )
    }

    private fun createArenaSelectorButton(player: Player): ItemStack = createButton(
        BedWars.getForCurrentVersion("SIGN", "SIGN", "OAK_SIGN"),
        0,
        Language.getMsg(player, PlayMenuMessages.SELECTOR_NAME),
        Language.getList(player, PlayMenuMessages.SELECTOR_LORE)
    )

    private fun createCloseButton(player: Player): ItemStack = createButton(
        "BARRIER",
        0,
        Language.getMsg(player, PlayMenuMessages.CLOSE_NAME),
        Language.getList(player, PlayMenuMessages.CLOSE_LORE)
    )

    private fun createReconnectButton(player: Player): ItemStack {
        val reJoin = ReJoin.getPlayer(player)
        val lore = if (reJoin == null) {
            Language.getList(player, PlayMenuMessages.RECONNECT_LORE_UNAVAILABLE)
        } else {
            Language.getList(player, PlayMenuMessages.RECONNECT_LORE_AVAILABLE)
                .map { it.replace("{arena}", reJoin.arena.displayName) }
        }

        return createButton(
            "GOLDEN_CARROT",
            0,
            Language.getMsg(player, PlayMenuMessages.RECONNECT_NAME),
            lore
        )
    }

    /**
     * Gray glass without a name, it only fills the gaps.
     */
    private fun createBlankPane(): ItemStack = createButton(
        BedWars.getForCurrentVersion("STAINED_GLASS_PANE", "STAINED_GLASS_PANE", "GRAY_STAINED_GLASS_PANE"),
        7,
        " ",
        emptyList()
    )

    private fun createButton(material: String, legacyData: Int, name: String, lore: List<String>): ItemStack {
        val item = BedWars.nms.createItemStack(material, 1, legacyData.toShort())
        val itemMeta = item.itemMeta ?: return item
        itemMeta.setDisplayName(name)
        if (lore.isNotEmpty()) {
            itemMeta.lore = lore
        }
        item.itemMeta = itemMeta
        return item
    }
}
