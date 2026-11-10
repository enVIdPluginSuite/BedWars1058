/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2021 Andrei Dascălu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Contact e-mail: andrew.dascalu@gmail.com
 */

package com.andrei1058.bedwars.commands.bedwars.subcmds.sensitive;

import com.andrei1058.bedwars.api.BedWars;
import com.andrei1058.bedwars.api.command.ParentCommand;
import com.andrei1058.bedwars.api.command.SubCommand;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.arena.Misc;
import com.andrei1058.bedwars.arena.SetupSession;
import com.andrei1058.bedwars.commands.bedwars.MainCommand;
import com.andrei1058.bedwars.configuration.Permissions;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class Reload extends SubCommand {

    public Reload(ParentCommand parent, String name) {
        super(parent, name);
        setPriority(11);
        showInList(true);
        setPermission(Permissions.PERMISSION_RELOAD);
        setDisplayInfo(Misc.msgHoverClick("§6 ▪ §7/" + getParent().getName() + " "+getSubCommandName()+"       §8 - §ereload configs",
                "§fReload config, language, shop and upgrades\nwithout restarting the server.\n§7Usage: /bw reload [all|config|lang|shop|upgrades]", "/"+ getParent().getName() + " "+getSubCommandName(), ClickEvent.Action.RUN_COMMAND));
    }

    @Override
    public boolean execute(String[] args, CommandSender s) {
        if (s instanceof Player) {
            if (!MainCommand.isLobbySet((Player) s)) return true;
        } else {
            if (!MainCommand.isLobbySet(null)) return true;
        }
        String scope = args.length == 0 ? "all" : args[0].toLowerCase();
        boolean all = scope.equals("all");
        boolean any = false;
        if (all || scope.equals("config")) {
            any = true;
            run(s, "config.yml", () -> com.andrei1058.bedwars.BedWars.config.reload());
            run(s, "generators.yml", () -> com.andrei1058.bedwars.BedWars.generators.reload());
            run(s, "sidebar and tab list", () -> {
                // header/footer, tab formats and sidebar lines read the config and language files live
                com.andrei1058.bedwars.sidebar.SidebarService svc = com.andrei1058.bedwars.sidebar.SidebarService.getInstance();
                svc.refreshTitles();
                svc.refreshPlaceholders();
                svc.refreshTabList();
                svc.refreshTabHeaderFooter();
            });
        }
        if (all || scope.equals("lang") || scope.equals("messages")) {
            any = true;
            for (Language l : Language.getLanguages()) {
                run(s, "language " + l.getLangName(), l::reload);
            }
        }
        if (all || scope.equals("shop")) {
            any = true;
            run(s, "shop.yml", () -> com.andrei1058.bedwars.BedWars.shop.reloadShop());
        }
        if (all || scope.equals("upgrades")) {
            any = true;
            run(s, "upgrades2.yml", com.andrei1058.bedwars.upgrades.UpgradesManager::reloadConfig);
        }
        if (!any) {
            s.sendMessage("§6 ▪ §7/" + getParent().getName() + " " + getSubCommandName() + " [all|config|lang|shop|upgrades]");
            return true;
        }
        s.sendMessage("§6 ▪ §7Arenas, maps and running games are not touched. Arena settings need an arena restart.");
        return true;
    }

    private static void run(CommandSender s, String what, Runnable action) {
        try {
            action.run();
            s.sendMessage("§6 ▪ §a" + what + " reloaded!");
        } catch (Throwable t) {
            s.sendMessage("§6 ▪ §c" + what + " failed: " + t.getMessage() + " (see console)");
            t.printStackTrace();
        }
    }

    @Override
    public List<String> getTabComplete() {
        return java.util.Arrays.asList("all", "config", "lang", "shop", "upgrades");
    }

    @Override
    public boolean canSee(CommandSender s, BedWars api) {
        if (s instanceof Player) {
            Player p = (Player) s;
            if (Arena.isInArena(p)) return false;
            if (SetupSession.isInSetupSession(p.getUniqueId())) return false;
        }
        return hasPermission(s);
    }
}
