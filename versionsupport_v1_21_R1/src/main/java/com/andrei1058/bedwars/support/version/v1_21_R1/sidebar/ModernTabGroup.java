package com.andrei1058.bedwars.support.version.v1_21_R1.sidebar;

import com.andrei1058.spigot.sidebar.PlaceholderProvider;
import com.andrei1058.spigot.sidebar.PlayerTab;
import com.andrei1058.spigot.sidebar.SidebarLine;
import com.andrei1058.spigot.sidebar.VersionedTabGroup;
import com.andrei1058.spigot.sidebar.WrappedSidebar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A scoreboard team mirrored on every receiver's board: players share prefix/suffix, collision and
 * name tag rules.
 */
final class ModernTabGroup implements VersionedTabGroup {

    private final WrappedSidebar sidebar;
    private final String identifier;
    private final SidebarLine prefix;
    private final SidebarLine suffix;
    private final Collection<PlaceholderProvider> placeholders;
    private final List<Player> members = new CopyOnWriteArrayList<>();
    private PushingRule pushing;
    private NameTagVisibility nameTag;
    private Player subject;

    ModernTabGroup(WrappedSidebar sidebar, String identifier, SidebarLine prefix, SidebarLine suffix,
                   PushingRule pushing, NameTagVisibility nameTag, Collection<PlaceholderProvider> placeholders) {
        this.sidebar = sidebar;
        this.identifier = identifier;
        this.prefix = prefix;
        this.suffix = suffix;
        this.pushing = pushing;
        this.nameTag = nameTag;
        this.placeholders = placeholders;
    }

    private String teamName() {
        return identifier.length() > 16 ? identifier.substring(0, 16) : identifier;
    }

    private String render(SidebarLine line, Player receiver) {
        if (line == null) {
            return "";
        }
        String text = line.getTrimReplacePlaceholders(receiver, null, placeholders);
        return text == null ? "" : text;
    }

    private void applyTo(Player receiver) {
        Scoreboard board = BoardRegistry.get(receiver);
        Team team = board.getTeam(teamName());
        if (team == null) {
            team = board.registerNewTeam(teamName());
        }
        String renderedPrefix = render(prefix, receiver);
        if (!renderedPrefix.equals(team.getPrefix())) {
            team.setPrefix(renderedPrefix);
        }
        String renderedSuffix = render(suffix, receiver);
        if (!renderedSuffix.equals(team.getSuffix())) {
            team.setSuffix(renderedSuffix);
        }
        if (team.getOption(Team.Option.COLLISION_RULE) != collision()) {
            team.setOption(Team.Option.COLLISION_RULE, collision());
        }
        if (team.getOption(Team.Option.NAME_TAG_VISIBILITY) != nameTagStatus()) {
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, nameTagStatus());
        }
        for (Player member : members) {
            if (!team.hasEntry(member.getName())) {
                team.addEntry(member.getName());
            }
        }
        for (String entry : team.getEntries()) {
            boolean keep = false;
            for (Player member : members) {
                if (member.getName().equals(entry)) {
                    keep = true;
                    break;
                }
            }
            if (!keep) {
                team.removeEntry(entry);
            }
        }
    }

    private Team.OptionStatus collision() {
        switch (pushing) {
            case ALWAYS:
                return Team.OptionStatus.ALWAYS;
            case PUSH_OTHER_TEAMS:
                return Team.OptionStatus.FOR_OTHER_TEAMS;
            case PUSH_OWN_TEAM:
                return Team.OptionStatus.FOR_OWN_TEAM;
            default:
                return Team.OptionStatus.NEVER;
        }
    }

    private Team.OptionStatus nameTagStatus() {
        switch (nameTag) {
            case NEVER:
                return Team.OptionStatus.NEVER;
            case HIDE_FOR_OTHER_TEAMS:
                return Team.OptionStatus.FOR_OWN_TEAM;
            case HIDE_FOR_OWN_TEAM:
                return Team.OptionStatus.FOR_OTHER_TEAMS;
            default:
                return Team.OptionStatus.ALWAYS;
        }
    }

    @Override
    public void sendCreateToPlayer(Player player) {
        applyTo(player);
    }

    @Override
    public void sendUserCreateToReceivers(Player player) {
        for (Player receiver : sidebar.getReceivers()) {
            applyTo(receiver);
        }
    }

    @Override
    public void sendUpdateToReceivers() {
        for (Player receiver : sidebar.getReceivers()) {
            applyTo(receiver);
        }
    }

    @Override
    public void sendRemoveToReceivers() {
        for (Player receiver : sidebar.getReceivers()) {
            removeFrom(receiver);
        }
    }

    private void removeFrom(Player receiver) {
        Team team = receiver.getScoreboard().getTeam(teamName());
        if (team != null) {
            team.unregister();
        }
        BoardRegistry.releaseIfEmpty(receiver);
    }

    @Override
    public boolean refreshContent() {
        return true;
    }

    @Override
    public void add(Player player) {
        if (!members.contains(player)) {
            members.add(player);
        }
        for (Player receiver : sidebar.getReceivers()) {
            Team team = receiver.getScoreboard().getTeam(teamName());
            if (team != null && !team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
        }
    }

    @Override
    public void remove(Player player) {
        members.remove(player);
        for (Player receiver : sidebar.getReceivers()) {
            Team team = receiver.getScoreboard().getTeam(teamName());
            if (team != null) {
                team.removeEntry(player.getName());
            }
        }
        if (player.isOnline()) {
            // the leaving player no longer receives this sidebar
            removeFrom(player);
        }
    }

    @Override
    public void setSubject(Player player) {
        this.subject = player;
    }

    @Override
    public Player getSubject() {
        return subject;
    }

    @Override
    public void setPushingRule(PushingRule rule) {
        this.pushing = rule;
        sendUpdateToReceivers();
    }

    @Override
    public void setNameTagVisibility(NameTagVisibility visibility) {
        this.nameTag = visibility;
        sendUpdateToReceivers();
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }
}
