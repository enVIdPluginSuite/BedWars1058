package com.andrei1058.bedwars.sidebar;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.support.version.ServerVersionResolver;
import com.andrei1058.spigot.sidebar.SidebarManager;
import com.andrei1058.spigot.sidebar.SidebarProvider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Creates the sidebar library manager.
 * <p>
 * The library picks its provider from the relocated CraftBukkit package, which no longer exists on
 * 1.20.5+ / 26.x. When that lookup fails on a modern server, the library manager (already registered as
 * the singleton by its constructor) gets the API-only provider injected instead.
 */
final class SidebarBootstrap {

    private static final String MODERN_PROVIDER =
            "com.andrei1058.bedwars.support.version.modern.sidebar.ModernSidebarProvider";

    private SidebarBootstrap() {
    }

    static SidebarManager init() {
        SidebarManager manager = null;
        try {
            manager = SidebarManager.init();
        } catch (Throwable ignored) {
            // handled below
        }
        if (manager != null) {
            return manager;
        }
        if (!ServerVersionResolver.isModern()) {
            return null;
        }
        try {
            manager = SidebarManager.getInstance();
            if (manager == null) {
                return null;
            }
            Constructor<?> constructor = Class.forName(MODERN_PROVIDER)
                    .getConstructor(org.bukkit.plugin.Plugin.class);
            SidebarProvider provider = (SidebarProvider) constructor.newInstance(BedWars.plugin);
            Field field = SidebarManager.class.getDeclaredField("sidebarProvider");
            field.setAccessible(true);
            field.set(manager, provider);
            return manager;
        } catch (Throwable t) {
            BedWars.plugin.getLogger().warning("Could not initialize the modern sidebar provider: " + t);
            return null;
        }
    }
}
