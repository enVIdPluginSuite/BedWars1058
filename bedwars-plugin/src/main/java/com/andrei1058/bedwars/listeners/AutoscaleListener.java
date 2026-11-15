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

package com.andrei1058.bedwars.listeners;

import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.events.gameplay.GameStateChangeEvent;
import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.arena.Instances;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class AutoscaleListener implements Listener {

    @EventHandler
    public void onPlaying(GameStateChangeEvent e) {
        if (e.getNewState() != GameState.playing) return;
        if (!Instances.enabled() || !Arena.canAutoScale(e.getArena().getArenaName())) return;
        int games = Arena.getGamesBeforeRestart();
        // outside of bungee auto-scale the restart counter is not used
        if (!BedWars.autoscale || games == -1 || games > 1) {
            new Arena(e.getArena().getArenaName(), null);
        }
    }
}
