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
 * 1.20.5+ / 26.x. When that lookup fails on a modern server, the library manager (already registered as
 * the singleton by its constructor) gets the API-only provider injected instead.
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
        return installModernProvider()
    }

    /**
     * The library constructor registers the manager before it fails to find a provider, so the
     * manager can be fetched and its provider field filled in afterwards.
     */
    private fun installModernProvider(): SidebarManager? {
        return try {
            val manager = SidebarManager.getInstance() ?: return null

            val providerClass = Class.forName(ServerVersionResolver.getModernSidebarProviderName())
            val provider = providerClass.getConstructor(Plugin::class.java).newInstance(BedWars.plugin) as SidebarProvider

            val providerField = SidebarManager::class.java.getDeclaredField("sidebarProvider")
            providerField.isAccessible = true
            providerField.set(manager, provider)
            manager
        } catch (failure: Throwable) {
            BedWars.plugin.logger.warning("Could not initialize the modern sidebar provider: $failure")
            null
        }
    }
}
