package com.andrei1058.bedwars.teamselector.teamselector

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.api.arena.GameState
import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.team.ITeam
import com.andrei1058.bedwars.api.language.Language
import com.andrei1058.bedwars.teamselector.api.events.TeamSelectorChooseEvent
import com.andrei1058.bedwars.teamselector.api.events.TeamSelectorOpenEvent
import com.andrei1058.bedwars.teamselector.configuration.Config
import com.andrei1058.bedwars.teamselector.configuration.Messages
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * The team selector item and the GUI where players pick a team.
 */
object TeamSelectorGUI {

    /** Marks the item that opens the GUI. */
    const val TEAM_SELECTOR_IDENTIFIER = "BWTEAMSELECTOR"

    /** Prefix of the items that join a team, followed by the team name. */
    const val TEAM_JOIN_IDENTIFIER = "BWJOIN_"

    /** Players that currently have the GUI open. */
    @JvmField
    val openGUIs: MutableList<UUID> = ArrayList()

    /**
     * Where the team items go: the inventory size and the slot of each team.
     */
    private class GuiLayout(val inventorySize: Int, val teamSlots: IntArray)

    private fun layoutFor(teamsCount: Int): GuiLayout = when (teamsCount) {
        2 -> GuiLayout(27, intArrayOf(11, 15))
        3 -> GuiLayout(27, intArrayOf(11, 13, 15))
        4 -> GuiLayout(27, intArrayOf(10, 12, 14, 16))
        5 -> GuiLayout(27, intArrayOf(11, 12, 13, 14, 15))
        6 -> GuiLayout(27, intArrayOf(10, 11, 12, 14, 15, 16))
        7 -> GuiLayout(27, intArrayOf(10, 11, 12, 13, 14, 15, 16))
        8 -> GuiLayout(45, intArrayOf(10, 12, 14, 16, 28, 30, 32, 34))
        9 -> GuiLayout(45, intArrayOf(10, 11, 12, 14, 15, 16, 30, 31, 32))
        10 -> GuiLayout(36, intArrayOf(11, 12, 13, 14, 15, 20, 21, 22, 23, 24))
        11 -> GuiLayout(36, intArrayOf(11, 12, 13, 14, 15, 19, 20, 21, 23, 24, 25))
        12 -> GuiLayout(45, intArrayOf(10, 11, 12, 14, 15, 16, 19, 20, 21, 23, 24, 25))
        else -> GuiLayout(9, intArrayOf())
    }

    /**
     * Open the GUI, or refresh the one the player already has open when [update] is true.
     */
    @JvmStatic
    fun openGUI(player: Player, update: Boolean) {
        val arena = BedWars.getAPI().arenaUtil.getArenaByPlayer(player) ?: return
        if (arena.status == GameState.playing || arena.status == GameState.restarting) return

        if (!update) {
            val openEvent = TeamSelectorOpenEvent(player)
            Bukkit.getPluginManager().callEvent(openEvent)
            if (openEvent.isCancelled()) return
        }

        Config.playSound(player, Config.GUI_OPEN_SOUND)
        openGUIs.add(player.uniqueId)

        val layout = layoutFor(arena.teams.size)
        val inventory = if (update) {
            BedWars.nms.getTopInventory(player)
        } else {
            Bukkit.createInventory(null, layout.inventorySize, Language.getMsg(player, Messages.GUI_NAME))
        } ?: return

        val usedSlots = HashSet<Int>()
        for ((index, team) in arena.teams.withIndex()) {
            if (index >= layout.teamSlots.size) break
            val teamItem = createTeamItem(player, arena, team) ?: continue
            inventory.setItem(layout.teamSlots[index], teamItem)
            usedSlots.add(layout.teamSlots[index])
        }

        fillEmptySlots(inventory, usedSlots, arena.getTeam(player))
        player.openInventory(inventory)
    }

    /**
     * The item representing a team, showing how many players picked it and who.
     */
    private fun createTeamItem(player: Player, arena: IArena, team: ITeam): ItemStack? {
        val teamColor = team.color.chat().toString()
        val selectedCount = TeamManager.getPlayersCount(team, arena).toString()
        val maxInTeam = arena.maxInTeam.toString()
        val teamName = team.getDisplayName(Language.getPlayerLanguage(player))

        var item = ItemStack(Material.valueOf(Config.config.getString(Config.SELECTOR_ITEM_STACK_MATERIAL)))
        item = BedWars.nms.colourItem(item, team)
        item = BedWars.nms.addCustomData(item, TEAM_JOIN_IDENTIFIER + team.name)

        val itemMeta = item.itemMeta ?: return null
        itemMeta.setDisplayName(
            Language.getMsg(player, Messages.CHOICE_NAME)
                .replace("{color}", teamColor).replace("{team}", teamName)
                .replace("{selected}", selectedCount).replace("{total}", maxInTeam)
        )

        val lore = ArrayList<String>()
        for (rawLine in Language.getList(player, Messages.CHOICE_LORE)) {
            val line = rawLine.replace("{color}", teamColor).replace("{team}", teamName)
                .replace("{selected}", selectedCount).replace("{total}", maxInTeam)
            if (!line.contains("{members}")) {
                lore.add(line)
                continue
            }
            // the members placeholder expands to one line per player, or to nothing when nobody picked the team
            val membersColor = ChatColor.getLastColors(line.replace("{members}", ""))
            for (member in TeamManager.getMembers(team, arena)) {
                lore.add(membersColor + member.displayName)
            }
        }
        itemMeta.lore = lore
        item.itemMeta = itemMeta
        return item
    }

    /**
     * Surround the team items with glass of the player's team color, once he picked a team.
     */
    private fun fillEmptySlots(inventory: org.bukkit.inventory.Inventory, usedSlots: Set<Int>, selected: ITeam?) {
        val glassMaterial = BedWars.getForCurrentVersion("STAINED_GLASS_PANE", "STAINED_GLASS_PANE", "BLACK_STAINED_GLASS_PANE")
        for (slot in 0 until inventory.size) {
            if (slot in usedSlots) continue
            if (selected == null) {
                inventory.setItem(slot, ItemStack(Material.AIR))
            } else {
                inventory.setItem(slot, BedWars.nms.colourItem(ItemStack(Material.valueOf(glassMaterial)), selected))
            }
        }
    }

    /**
     * Register that a player wants to play in a team.
     *
     * @return false when the choice was refused.
     */
    @JvmStatic
    fun joinTeam(player: Player, teamName: String): Boolean {
        val arena = BedWars.getAPI().arenaUtil.getArenaByPlayer(player) ?: return false
        if (arena.status == GameState.playing || arena.status == GameState.restarting) return false

        // the countdown task only exists while starting, and it may be gone for a moment while the state changes
        val countdownTask = arena.startingTask
        if (arena.status == GameState.starting && countdownTask != null && countdownTask.countdown < 2) return false

        val team = arena.getTeam(teamName) ?: return false
        val currentChoice = TeamManager.getPlayerTeam(player, arena)

        if (team == currentChoice) {
            player.sendMessage(Language.getMsg(player, Messages.ALREADY_IN_TEAM))
            return false
        }

        if (BedWars.getParty().hasParty(player)) {
            player.sendMessage(Language.getMsg(player, Messages.PARTY_DENIED))
            return false
        }

        val teamDisplayName = team.getDisplayName(Language.getPlayerLanguage(player))
        val teamColor = team.color.chat().toString()
        val playersInTeam = TeamManager.getPlayersCount(team, arena)

        if (playersInTeam >= arena.maxInTeam) {
            player.sendMessage(
                Language.getMsg(player, Messages.TEAM_FULL).replace("{color}", teamColor).replace("{team}", teamDisplayName)
            )
            return false
        }

        // do not allow a team to get ahead of another one
        if (Config.config.getBoolean(Config.BALANCE_TEAMS)) {
            val anotherTeamHasLess = arena.teams.any { other ->
                other != team && playersInTeam > TeamManager.getPlayersCount(other, arena)
            }
            if (anotherTeamHasLess) {
                player.sendMessage(Language.getMsg(player, Messages.TEAM_NOT_BALANCED))
                return false
            }
        }

        if (currentChoice != null && !Config.config.getBoolean(Config.ALLOW_TEAM_CHANGE)) {
            player.sendMessage(Language.getMsg(player, Messages.SWITCH_DISABLED))
            return false
        }

        val chooseEvent = TeamSelectorChooseEvent(player, arena, team, currentChoice)
        Bukkit.getPluginManager().callEvent(chooseEvent)
        if (chooseEvent.isCancelled()) return false

        TeamManager.setPlayerTeam(player, arena, team)
        giveItem(player, team)

        player.sendMessage(
            Language.getMsg(player, Messages.TEAM_JOIN)
                .replace("{color}", teamColor).replace("{team}", teamDisplayName)
                .replace("{selected}", TeamManager.getMembers(team, arena).toString())
                .replace("{total}", arena.maxInTeam.toString())
        )
        return true
    }

    /**
     * Refresh the GUI of everyone who has it open.
     */
    @JvmStatic
    fun updateGUIs() {
        for (playerId in ArrayList(openGUIs)) {
            val player = Bukkit.getPlayer(playerId)
            if (player == null) {
                openGUIs.remove(playerId)
                continue
            }
            openGUI(player, true)
        }
    }

    /**
     * Give the player the item that opens the GUI, colored as [team] when he already picked one.
     */
    @JvmStatic
    fun giveItem(player: Player, team: ITeam?) {
        var item = try {
            ItemStack(Material.valueOf(Config.config.getString(Config.SELECTOR_ITEM_STACK_MATERIAL)))
        } catch (invalidMaterial: Exception) {
            BedWars.plugin.logger.severe("Team-Selector Material is invalid: ${Config.config.getString(Config.SELECTOR_ITEM_STACK_MATERIAL)}")
            return
        }

        if (team != null && Config.config.getBoolean(Config.GIVE_SELECTOR_SELECTED_TEAM_COLOR)) {
            item = BedWars.nms.colourItem(item, team)
        }

        val itemMeta = item.itemMeta ?: return
        itemMeta.lore = Language.getList(player, Messages.SELECTOR_LORE)
        itemMeta.setDisplayName(Language.getMsg(player, Messages.SELECTOR_NAME))
        item.itemMeta = itemMeta
        item = BedWars.nms.addCustomData(item, TEAM_SELECTOR_IDENTIFIER)
        player.inventory.setItem(Config.config.getInt(Config.SELECTOR_SLOT), item)
    }
}
