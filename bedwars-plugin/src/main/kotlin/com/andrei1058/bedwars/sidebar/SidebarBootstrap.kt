package com.andrei1058.bedwars.sidebar

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.support.version.ServerVersionResolver
import com.andrei1058.spigot.sidebar.SidebarManager
import com.andrei1058.spigot.sidebar.SidebarProvider
import org.bukkit.plugin.Plugin

/**
 * Creates the sidebar library manager.
 *
 * The library picks its provider from the relocated CraftBukkit package, which no longer exists on
 * 1.20.5+ / 26.x. When that lookup fails on a modern server, the manager is created with the
 * API-only provider instead.
 */
object SidebarBootstrap {

    /**
     * @return the sidebar manager, or null when the sidebar cannot be provided on this server.
     */
    @JvmStatic
    fun init(): SidebarManager? {
        val libraryManager = try {
            SidebarManager.init()
        } catch (noProviderForThisServer: Throwable) {
            null
        }
        if (libraryManager != null) {
            return libraryManager
        }
        if (!ServerVersionResolver.isModern()) {
            return null
        }
        return createModernManager()
    }

    private fun createModernManager(): SidebarManager? {
        return try {
            val providerClass = Class.forName(ServerVersionResolver.getModernSidebarProviderName())
            val provider = providerClass.getConstructor(Plugin::class.java).newInstance(BedWars.plugin) as SidebarProvider
            SidebarManager.init(provider)
        } catch (failure: Throwable) {
            BedWars.plugin.logger.warning("Could not initialize the modern sidebar provider: $failure")
            null
        }
    }
}
