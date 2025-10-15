package com.andrei1058.bedwars.support.version.modern.sidebar;

import com.andrei1058.bedwars.support.version.modern.ModernCompat;
import com.andrei1058.spigot.sidebar.PlaceholderProvider;
import com.andrei1058.spigot.sidebar.PlayerTab;
import com.andrei1058.spigot.sidebar.ScoreLine;
import com.andrei1058.spigot.sidebar.Sidebar;
import com.andrei1058.spigot.sidebar.SidebarLine;
import com.andrei1058.spigot.sidebar.SidebarObjective;
import com.andrei1058.spigot.sidebar.SidebarProvider;
import com.andrei1058.spigot.sidebar.VersionedTabGroup;
import com.andrei1058.spigot.sidebar.WrappedSidebar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Objective;

import java.util.Collection;

/**
 * Sidebar implementation for 1.21+ and 26.x built purely on the Bukkit scoreboard API, so it does not
 * depend on packet classes that change on every release. Plugged into the sidebar library by
 * {@code SidebarBootstrap} when the library has no provider for the running server.
 */
public class ModernSidebarProvider extends SidebarProvider {

    private final boolean paper = ModernCompat.isPaper();

    public ModernSidebarProvider(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent e) {
                BoardRegistry.forget(e.getPlayer().getUniqueId());
            }
        }, plugin);
    }

    @Override
    public Sidebar createSidebar(SidebarLine title, Collection<SidebarLine> lines,
                                 Collection<PlaceholderProvider> placeholders) {
        return new WrappedSidebar(title, lines, placeholders);
    }

    @Override
    public SidebarObjective createObjective(WrappedSidebar sidebar, String name, boolean health,
                                            SidebarLine title, int type) {
        return new ModernObjective(sidebar, name, health, title, type);
    }

    @Override
    public ScoreLine createScoreLine(WrappedSidebar sidebar, SidebarLine line, int score, String color) {
        return new ModernScoreLine(sidebar, line, score, color, paper);
    }

    @Override
    public void sendScore(WrappedSidebar sidebar, String playerName, int score) {
        SidebarObjective health = sidebar.getHealthObjective();
        if (!(health instanceof ModernObjective)) {
            return;
        }
        for (Player receiver : sidebar.getReceivers()) {
            Objective objective = ((ModernObjective) health).find(receiver);
            if (objective != null) {
                objective.getScore(playerName).setScore(score);
            }
        }
    }

    @Override
    public VersionedTabGroup createPlayerTab(WrappedSidebar sidebar, String identifier, SidebarLine prefix,
                                             SidebarLine suffix, PlayerTab.PushingRule pushingRule,
                                             PlayerTab.NameTagVisibility nameTagVisibility,
                                             Collection<PlaceholderProvider> placeholders) {
        return new ModernTabGroup(sidebar, identifier, prefix, suffix, pushingRule, nameTagVisibility, placeholders);
    }

    @Override
    public void sendHeaderFooter(Player player, String header, String footer) {
        player.setPlayerListHeaderFooter(header == null ? "" : header, footer == null ? "" : footer);
    }
}
