package com.andrei1058.vipfeatures.api;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public abstract class MiniGame {

    private final Plugin plugin;

    public MiniGame(Plugin plugin) {
        this.plugin = plugin;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public abstract boolean isPlaying(Player player);

    public abstract boolean hasBoosters();

    public abstract String getDisplayName();
}
