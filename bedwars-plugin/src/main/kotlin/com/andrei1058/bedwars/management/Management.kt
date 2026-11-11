package com.andrei1058.bedwars.management

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.management.arena.ArenaOps
import com.andrei1058.bedwars.management.arena.ArenaRepository
import com.andrei1058.bedwars.management.arena.BulkRunner
import com.andrei1058.bedwars.management.bedwars.BedWarsHook
import com.andrei1058.bedwars.management.command.CommandInterceptor
import com.andrei1058.bedwars.management.gui.ArenaSelectorMenu
import com.andrei1058.bedwars.management.gui.MenuListener
import com.andrei1058.bedwars.management.session.ChatPrompts
import com.andrei1058.bedwars.management.session.SessionManager
import com.andrei1058.bedwars.management.util.Text
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
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

    /** Reads management.yml, writing the bundled default first when it is missing. */
    private fun loadConfig() {
        if (!configFile.isFile) owner.saveResource("management.yml", false)
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
