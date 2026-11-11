package com.andrei1058.bedwars.management

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.management.arena.ArenaOps
import com.andrei1058.bedwars.management.arena.ArenaRepository
import com.andrei1058.bedwars.management.arena.BulkRunner
import com.andrei1058.bedwars.management.bedwars.BedWarsHook
import com.andrei1058.bedwars.management.command.CommandInterceptor
import com.andrei1058.bedwars.management.gui.ArenaBrowserMenu
import com.andrei1058.bedwars.management.gui.ArenaSelectorMenu
import com.andrei1058.bedwars.management.gui.MenuListener
import com.andrei1058.bedwars.management.session.ChatPrompts
import com.andrei1058.bedwars.management.session.SessionManager
import com.andrei1058.bedwars.management.util.Text
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.io.InputStreamReader
import java.util.logging.Logger

/**
 * Arena, group and bulk management built into BedWars1058 (it used to be the separate
 * BedWars1058-ArenaManagement plugin). Configuration lives in `plugins/BedWars1058/management.yml`.
 */
class Management(val owner: BedWars) {
    val logger: Logger get() = owner.logger
    val server get() = owner.server

    private val configFile = File(owner.dataFolder, "management.yml")
    var config: YamlConfiguration = YamlConfiguration()
        private set

    lateinit var settings: Settings
        private set
    lateinit var hook: BedWarsHook
        private set
    lateinit var repository: ArenaRepository
        private set
    lateinit var sessions: SessionManager
        private set
    lateinit var prompts: ChatPrompts
        private set
    lateinit var runner: BulkRunner
        private set
    lateinit var ops: ArenaOps
        private set
    lateinit var interceptor: CommandInterceptor
        private set

    /**
     * BedWars1058-MapLoader, if installed. It lists "parked" arenas in BedWars1058 without a loaded world and
     * wakes them through command / join hooks, so everything here goes through those hooks (see ArenaOps).
     */
    val mapLoader: Plugin? get() = server.pluginManager.getPlugin("BedWars1058-MapLoader")

    /** BedWars1058-MultiGroup, if installed: it owns the variants of maps shared between groups. */
    val multiGroup: Plugin? get() = server.pluginManager.getPlugin("BedWars1058-MultiGroup")

    /** `/bw gui` menus currently open; the refresh task keeps their player counts live. */
    val liveSelectors: MutableSet<ArenaSelectorMenu> = LinkedHashSet()

    private var refreshTask: BukkitTask? = null

    fun enable() {
        loadConfig()
        settings = Settings(this)
        hook = BedWarsHook(this)
        repository = ArenaRepository(this)
        sessions = SessionManager()
        prompts = ChatPrompts(this)
        runner = BulkRunner(this)
        ops = ArenaOps(this)
        interceptor = CommandInterceptor(this)

        server.pluginManager.registerEvents(MenuListener(this), owner)
        server.pluginManager.registerEvents(prompts, owner)
        server.pluginManager.registerEvents(interceptor, owner)
        startRefreshTask()
    }

    fun disable() {
        refreshTask?.cancel()
        // Close our menus so nobody is left holding a dead inventory after a reload.
        for (menu in liveSelectors.toList()) menu.player.closeInventory()
    }

    /**
     * Reads management.yml. When it is missing, the config of the standalone BedWars1058-ArenaManagement plugin is
     * adopted if there is one, otherwise the bundled default is written.
     */
    private fun loadConfig() {
        if (!configFile.isFile) {
            val old = File(owner.dataFolder.parentFile, "BedWars1058-ArenaManagement/config.yml")
            if (old.isFile) {
                runCatching { old.copyTo(configFile) }.onSuccess { logger.info("Imported the ArenaManagement config from ${old.path}.") }
            }
            if (!configFile.isFile) owner.saveResource("management.yml", false)
        }
        val loaded = YamlConfiguration.loadConfiguration(configFile)
        owner.getResource("management.yml")?.use { stream ->
            loaded.setDefaults(YamlConfiguration.loadConfiguration(InputStreamReader(stream, Charsets.UTF_8)))
        }
        config = loaded
    }

    fun reload() {
        loadConfig()
        repository.invalidate()
        startRefreshTask()
    }

    private fun startRefreshTask() {
        refreshTask?.cancel()
        refreshTask = server.scheduler.runTaskTimer(
            owner,
            Runnable {
                for (menu in liveSelectors.toList()) {
                    if (!menu.player.isOnline) liveSelectors.remove(menu) else menu.live()
                }
            },
            settings.guiRefreshTicks,
            settings.guiRefreshTicks
        )
    }

    /** Runs [block] on the main thread after [ticks]. */
    fun later(ticks: Long, block: () -> Unit) {
        server.scheduler.runTaskLater(owner, Runnable { block() }, ticks)
    }

    fun msg(target: CommandSender, text: String) {
        target.sendMessage(settings.prefix + Text.color(text))
    }

    /** `/arenamanagement <reload|menu|gui [group]>`, same as the standalone plugin's command. */
    fun onCommand(sender: CommandSender, label: String, args: List<String>): Boolean {
        val sub = args.firstOrNull()?.lowercase()
        when (sub) {
            "reload" -> {
                if (!sender.hasPermission("arenamanagement.admin")) {
                    msg(sender, "&cYou do not have permission.")
                    return true
                }
                reload()
                msg(sender, "&aConfig reloaded.")
            }
            "menu", "gui" -> {
                if (sender !is Player) {
                    msg(sender, "&cOnly players can open menus.")
                    return true
                }
                if (sub == "menu") {
                    if (!sender.hasPermission(settings.permission)) {
                        msg(sender, "&cYou do not have permission.")
                        return true
                    }
                    ArenaBrowserMenu(this, sender).open()
                } else {
                    interceptor.openSelector(sender, args.getOrNull(1))
                }
            }
            else -> msg(sender, "&7Usage: &e/$label <reload|menu|gui [group]>")
        }
        return true
    }

    companion object {
        @JvmStatic
        var instance: Management? = null
            private set

        @JvmStatic
        fun start(owner: BedWars) {
            val management = Management(owner)
            management.enable()
            instance = management
        }

        @JvmStatic
        fun stop() {
            instance?.disable()
            instance = null
        }
    }
}
