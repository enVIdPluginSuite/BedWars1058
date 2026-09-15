package com.andrei1058.bedwars.teamselector.configuration

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.api.configuration.ConfigManager
import org.bukkit.entity.Player
import java.io.File
import java.util.logging.Level

/**
 * Settings of the team selector. They live in their own file, the same one the standalone team selector addon
 * used, so existing configurations keep working.
 */
object Config {

    const val SELECTOR_ITEM_STACK_MATERIAL = "team-selector-item-stack"
    const val SELECTOR_SLOT = "team-selector-slot"
    const val GIVE_SELECTOR_SELECTED_TEAM_COLOR = "give-team-color"
    const val ALLOW_TEAM_CHANGE = "allow-team-change"
    const val ALLOW_MOVE_TROUGH_INVENTORY = "allow-move-in-inventory"
    const val BALANCE_TEAMS = "balance-teams"
    const val GUI_OPEN_SOUND = "gui-open-sound"
    const val SUCCESS_SOUND = "success-sound"
    const val ERROR_SOUND = "error-sound"

    private const val CONFIG_DIRECTORY = "plugins/BedWars1058/Addons/TeamSelector"

    lateinit var config: ConfigManager

    /**
     * False when the built-in selector is off, for example while the old addon is still installed.
     */
    @JvmStatic
    fun isLoaded(): Boolean = ::config.isInitialized

    /**
     * Create the configuration file and fill in the missing defaults.
     */
    @JvmStatic
    fun addDefaultConfig() {
        File(CONFIG_DIRECTORY).mkdirs()

        config = ConfigManager(BedWars.plugin, "config", CONFIG_DIRECTORY)
        val yml = config.yml
        yml.options().header(
            "Team Selector for BedWars1058 (built into the plugin).\n\nDocumentation:\n" +
                    "$SELECTOR_ITEM_STACK_MATERIAL: WOOL - The material you want the team-selector item be.\n" +
                    "$SELECTOR_SLOT: 0 - The slot where to put the item.\n" +
                    "$GIVE_SELECTOR_SELECTED_TEAM_COLOR: true - True if you the selector to have the selected team's color.\n" +
                    "$ALLOW_TEAM_CHANGE: true - True if you want to allow players to change selected team.\n" +
                    "$ALLOW_MOVE_TROUGH_INVENTORY: false - True if you want to allow players to move it in inventory.\n" +
                    "$BALANCE_TEAMS: true - True if you want to have balanced teams size.\n" +
                    "$GUI_OPEN_SOUND: BLOCK_SHULKER_BOX_OPEN - The sound to be played when you open the team selector.\n" +
                    "$SUCCESS_SOUND: BLOCK_SHULKER_BOX_CLOSE - The sound to be played when you select a team successfully.\n" +
                    "$ERROR_SOUND: BLOCK_ANVIL_DESTROY - The sound to be played when you can't select a team.\n" +
                    "Sounds for latest version: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html\n" +
                    "Sounds for 1.8 or older: https://docs.codelanx.com/Bukkit/1.8/org/bukkit/Sound.html"
        )

        yml.addDefault(SELECTOR_ITEM_STACK_MATERIAL, BedWars.getForCurrentVersion("WOOL", "WOOL", "WHITE_WOOL"))
        yml.addDefault(SELECTOR_SLOT, 4)
        yml.addDefault(GIVE_SELECTOR_SELECTED_TEAM_COLOR, true)
        yml.addDefault(ALLOW_TEAM_CHANGE, true)
        yml.addDefault(ALLOW_MOVE_TROUGH_INVENTORY, false)
        yml.addDefault(BALANCE_TEAMS, true)
        yml.addDefault(GUI_OPEN_SOUND, BedWars.getForCurrentVersion("CHEST_OPEN", "CHEST_OPEN", "BLOCK_SHULKER_BOX_OPEN"))
        yml.addDefault(SUCCESS_SOUND, BedWars.getForCurrentVersion("CHEST_CLOSE", "CHEST_CLOSE", "BLOCK_SHULKER_BOX_CLOSE"))
        yml.addDefault(ERROR_SOUND, BedWars.getForCurrentVersion("ANVIL_BREAK", "ANVIL_BREAK", "BLOCK_ANVIL_DESTROY"))
        yml.options().copyDefaults(true)
        config.save()
    }

    /**
     * Play the sound configured at [path]. The sound is resolved through the version support, so it works on
     * servers where the sound registry changed.
     */
    @JvmStatic
    fun playSound(player: Player?, path: String) {
        if (player == null) return
        val soundName = config.getString(path)
        try {
            player.playSound(player.location, BedWars.nms.getSound(soundName), 1f, 1f)
        } catch (invalidSound: Exception) {
            BedWars.plugin.logger.log(Level.SEVERE, "$soundName it's not a valid sound for your server version!")
        }
    }
}
