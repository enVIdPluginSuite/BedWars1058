package com.andrei1058.bedwars.commands.bedwars.subcmds.regular;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.command.ParentCommand;
import com.andrei1058.bedwars.api.command.SubCommand;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.arena.SetupSession;
import com.andrei1058.bedwars.commands.bedwars.MainCommand;
import com.andrei1058.bedwars.listeners.arenaselector.SpectateMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /bw spectate [arena] - pick a running game to watch.
 */
public class CmdSpectate extends SubCommand {

    public CmdSpectate(ParentCommand parent, String name) {
        super(parent, name);
        setPriority(19);
        showInList(false);
        setDisplayInfo(MainCommand.createTC("§6 ▪ §7/" + MainCommand.getInstance().getName() + " spectate §e[arena]",
                "/" + getParent().getName() + " " + getSubCommandName(),
                "§fOpen a menu with the games you can spectate.\n§fAdd an arena name to only list its games."));
    }

    @Override
    public boolean execute(String[] args, CommandSender s) {
        if (s instanceof ConsoleCommandSender) return false;
        Player p = (Player) s;
        String arena = null;
        if (args.length >= 1) {
            arena = args[0];
            boolean known = false;
            for (IArena a : Arena.getArenas()) {
                if (a.getArenaName().equalsIgnoreCase(arena)) {
                    arena = a.getArenaName();
                    known = true;
                    break;
                }
            }
            if (!known) {
                p.sendMessage(Language.getMsg(p, SpectateMenu.MSG_ARENA_UNKNOWN).replace("{name}", args[0]));
                return true;
            }
        }
        SpectateMenu.open(p, arena);
        return true;
    }

    @Override
    public List<String> getTabComplete() {
        List<String> tab = new ArrayList<>();
        for (IArena a : Arena.getArenas()) {
            if (!tab.contains(a.getArenaName())) tab.add(a.getArenaName());
        }
        return tab;
    }

    @Override
    public boolean canSee(CommandSender s, com.andrei1058.bedwars.api.BedWars api) {
        if (s instanceof ConsoleCommandSender) return false;
        Player p = (Player) s;
        if (Arena.isInArena(p)) return false;
        if (SetupSession.isInSetupSession(p.getUniqueId())) return false;
        return hasPermission(s);
    }
}
