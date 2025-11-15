package com.andrei1058.bedwars.support.version.v1_21_R1.sidebar;

import com.andrei1058.spigot.sidebar.ScoreLine;
import com.andrei1058.spigot.sidebar.ScoredLine;
import com.andrei1058.spigot.sidebar.SidebarLine;
import com.andrei1058.spigot.sidebar.WrappedSidebar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One sidebar row: a unique invisible entry (the color code) rendered through a team prefix, with the
 * score used for ordering.
 */
final class ModernScoreLine implements ScoreLine {

    private final WrappedSidebar sidebar;
    private final String color;
    private SidebarLine line;
    private int score;
    private final boolean paper;
    /** last text pushed to each receiver, to avoid redundant packets */
    private final Map<UUID, String> sent = new ConcurrentHashMap<>();
    private final Map<UUID, String> sentRight = new ConcurrentHashMap<>();

    ModernScoreLine(WrappedSidebar sidebar, SidebarLine line, int score, String color, boolean paper) {
        this.sidebar = sidebar;
        this.line = line;
        this.score = score;
        this.color = color;
        this.paper = paper;
    }

    private String teamName() {
        // team and entry names stay unique per row, the color code is unique within a sidebar
        return "bwl" + color.replace("§", "");
    }

    private String entry() {
        return color + "§r";
    }

    private String render(Player player) {
        String text = line.getTrimReplacePlaceholders(player, null, sidebar.getPlaceholders());
        return text == null ? "" : text;
    }

    @Override
    public SidebarLine getLine() {
        return line;
    }

    @Override
    public void setLine(SidebarLine line) {
        this.line = line;
    }

    @Override
    public int getScoreAmount() {
        return score;
    }

    @Override
    public void setScoreAmount(int score) {
        this.score = score;
        // the library shifts scores around without asking for an update (removing a line, for example)
        for (Player player : sidebar.getReceivers()) {
            if (!sent.containsKey(player.getUniqueId())) {
                continue;
            }
            Objective objective = sidebar.getSidebarObjective() instanceof ModernObjective
                    ? ((ModernObjective) sidebar.getSidebarObjective()).find(player) : null;
            if (objective != null) {
                Score row = objective.getScore(entry());
                if (!row.isScoreSet() || row.getScore() != score) {
                    row.setScore(score);
                }
            }
        }
    }

    @Override
    public void sendCreateToAllReceivers() {
        for (Player player : sidebar.getReceivers()) {
            sendCreate(player);
        }
    }

    @Override
    public void sendCreate(Player player) {
        Objective objective = sidebar.getSidebarObjective() instanceof ModernObjective
                ? ((ModernObjective) sidebar.getSidebarObjective()).find(player) : null;
        if (objective == null) {
            return;
        }
        Scoreboard board = objective.getScoreboard();
        Team team = board.getTeam(teamName());
        if (team == null) {
            team = board.registerNewTeam(teamName());
            // new board or new row: whatever was cached no longer exists client side
            sent.remove(player.getUniqueId());
            sentRight.remove(player.getUniqueId());
        }
        if (!team.hasEntry(entry())) {
            team.addEntry(entry());
        }
        apply(player, team, objective);
    }

    private void apply(Player player, Team team, Objective objective) {
        String text = render(player);
        String previous = sent.put(player.getUniqueId(), text);
        if (!text.equals(previous)) {
            team.setPrefix(text);
        }
        Score row = objective.getScore(entry());
        if (row.getScore() != score || !row.isScoreSet()) {
            row.setScore(score);
        }
        if (paper) {
            String right = line instanceof ScoredLine ? ((ScoredLine) line).getScore() : null;
            right = right == null ? "" : right;
            if (!right.equals(sentRight.put(player.getUniqueId(), right))) {
                PaperNumbers.apply(row, right);
            }
        }
    }

    @Override
    public boolean setContent(SidebarLine line) {
        this.line = line;
        return refreshContent();
    }

    @Override
    public void sendUpdateToAllReceivers() {
        for (Player player : sidebar.getReceivers()) {
            sendUpdate(player);
        }
    }

    @Override
    public void sendUpdate(Player player) {
        sendCreate(player);
    }

    @Override
    public void sendRemoveToAllReceivers() {
        for (Player player : sidebar.getReceivers()) {
            sendRemove(player);
        }
    }

    @Override
    public void sendRemove(Player player) {
        sent.remove(player.getUniqueId());
        sentRight.remove(player.getUniqueId());
        Scoreboard board = player.getScoreboard();
        Objective objective = board.getObjective(sidebar.getSidebarObjective().getName());
        if (objective != null) {
            objective.getScoreboard().resetScores(entry());
        }
        Team team = board.getTeam(teamName());
        if (team != null) {
            team.unregister();
        }
        BoardRegistry.releaseIfEmpty(player);
    }

    @Override
    public String getColor() {
        return color;
    }

    @Override
    public boolean refreshContent() {
        return true;
    }

    @Override
    public int compareTo(ScoreLine other) {
        // ascending: the library gives a new line the score of the first one (the lowest) and then shifts the others up
        return Integer.compare(score, other.getScoreAmount());
    }
}
