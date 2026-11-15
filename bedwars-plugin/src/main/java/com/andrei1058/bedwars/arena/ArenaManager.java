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

package com.andrei1058.bedwars.arena;

import org.bukkit.Bukkit;

import java.io.File;
import java.text.SimpleDateFormat;

public class ArenaManager {

    private int gid = 0;
    private String stamp = "";

    /**
     * Generates a world name that is unique for today, not loaded and not present on disk.
     */
    public synchronized String generateGameID() {
        String now = new SimpleDateFormat("'y'yy'm'MM'd'dd").format(System.currentTimeMillis());
        if (!now.equals(this.stamp)) {
            this.stamp = now;
            this.gid = 0;
        }
        String id;
        do {
            id = "bw_temp_" + this.stamp + "g" + gid++;
        } while (Bukkit.getWorld(id) != null || new File(Bukkit.getWorldContainer(), id).exists()
                || Arena.getArenaByIdentifier(id) != null);
        return id;
    }

}
