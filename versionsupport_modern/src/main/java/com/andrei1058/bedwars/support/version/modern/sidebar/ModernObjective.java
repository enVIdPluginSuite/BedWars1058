package com.andrei1058.bedwars.support.version.modern.sidebar;

import com.andrei1058.spigot.sidebar.SidebarLine;
import com.andrei1058.spigot.sidebar.SidebarObjective;
import com.andrei1058.spigot.sidebar.WrappedSidebar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ModernObjective implements SidebarObjective {

    private final WrappedSidebar sidebar;
    private final String name;
    private final boolean health;
    private final int type;
    private SidebarLine title;
    private String renderedTitle = "";
    private final Map<UUID, Boolean> receivers = new ConcurrentHashMap<>();

    ModernObjective(WrappedSidebar sidebar, String name, boolean health, SidebarLine title, int type) {
        this.sidebar = sidebar;
        this.name = name;
        this.health = health;
        this.title = title;
        this.type = type;
    }

    boolean isHealth() {
        return health;
    }

    @Override
    public void setTitle(SidebarLine title) {
        this.title = title;
    }

    @Override
    public SidebarLine getTitle() {
        return title;
    }

    private String render(Player player) {
        String text = title.getTrimReplacePlaceholders(player, null, sidebar.getPlaceholders());
        return text == null ? "" : text;
    }

    Objective find(Player player) {
        return BoardRegistry.get(player).getObjective(name);
    }

    @Override
    public void sendCreate(Player player) {
        Scoreboard board = BoardRegistry.get(player);
        Objective objective = board.getObjective(name);
        if (objective != null) {
            objective.unregister();
        }
        if (health) {
            // scores are pushed by the plugin (sendScore), so the criteria must not be read-only like Criteria.HEALTH
            objective = board.registerNewObjective(name, Criteria.DUMMY, render(player), RenderType.HEARTS);
            // type 2 is the tab list, anything else (health2) is shown under the name tag
            objective.setDisplaySlot(name.equals("health") ? DisplaySlot.PLAYER_LIST : DisplaySlot.BELOW_NAME);
            if (!name.equals("health")) {
                objective.setRenderType(RenderType.INTEGER);
            }
        } else {
            objective = board.registerNewObjective(name, Criteria.DUMMY, render(player));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        receivers.put(player.getUniqueId(), Boolean.TRUE);
    }

    @Override
    public void sendUpdate() {
        for (Player player : sidebar.getReceivers()) {
            Objective objective = find(player);
            if (objective != null) {
                objective.setDisplayName(render(player));
            }
        }
    }

    @Override
    public void sendRemove(Player player) {
        receivers.remove(player.getUniqueId());
        Scoreboard board = player.getScoreboard();
        Objective objective = board.getObjective(name);
        if (objective != null) {
            objective.unregister();
        }
        BoardRegistry.releaseIfEmpty(player);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean refreshTitle() {
        String next = title.getTrimReplacePlaceholders(null, null, sidebar.getPlaceholders());
        next = next == null ? "" : next;
        boolean changed = !next.equals(renderedTitle);
        renderedTitle = next;
        return changed || title.isHasPlaceholders();
    }
}
