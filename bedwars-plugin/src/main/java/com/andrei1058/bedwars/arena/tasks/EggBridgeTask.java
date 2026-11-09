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

package com.andrei1058.bedwars.arena.tasks;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.TeamColor;
import com.andrei1058.bedwars.api.events.gameplay.EggBridgeBuildEvent;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.arena.Misc;
import com.andrei1058.bedwars.configuration.Sounds;
import com.andrei1058.bedwars.listeners.EggBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import static com.andrei1058.bedwars.BedWars.nms;

@SuppressWarnings("WeakerAccess")
public class EggBridgeTask implements Runnable {

    /** Longest horizontal distance from the thrower the bridge may reach. */
    private static final double MAX_DISTANCE = 30.0D;
    /** The bridge ends after this many ticks. */
    private static final int MAX_TICKS = 100;
    /** Blocks are placed at least this close along the path, so a fast egg leaves no gaps. */
    private static final double STEP = 0.5D;

    private Egg projectile;
    private TeamColor teamColor;
    private Player player;
    private IArena arena;
    private BukkitTask task;

    /** Where the egg was on the previous tick. */
    private Vector last;
    /** Once the egg hit something it is simulated by hand, so that it flies on through blocks. */
    private boolean virtual = false;
    private Vector virtualVelocity;
    private int ticks = 0;
    private boolean finished = false;

    public EggBridgeTask(Player player, Egg projectile, TeamColor teamColor) {
        IArena a = Arena.getArenaByPlayer(player);
        if (a == null) return;
        this.arena = a;
        this.projectile = projectile;
        this.teamColor = teamColor;
        this.player = player;
        this.last = projectile.getLocation().toVector();
        task = Bukkit.getScheduler().runTaskTimer(BedWars.plugin, this, 0, 1);
    }

    public TeamColor getTeamColor() {
        return teamColor;
    }

    public Egg getProjectile() {
        return projectile;
    }

    public Player getPlayer() {
        return player;
    }

    public IArena getArena() {
        return arena;
    }

    /**
     * The egg hit a block or an entity (and is about to disappear): carry on along its path as if nothing was in
     * the way.
     */
    public void onProjectileHit() {
        if (virtual || finished) return;
        virtual = true;
        virtualVelocity = projectile.getVelocity().clone();
    }

    @Override
    public void run() {
        if (finished) return;
        if (!arena.isPlayer(getPlayer()) || ++ticks > MAX_TICKS) {
            finish();
            return;
        }

        World world = getPlayer().getWorld();
        Vector now;
        if (virtual) {
            // same physics as a thrown egg: move, then drag and gravity
            now = last.clone().add(virtualVelocity);
            virtualVelocity.multiply(0.99D).setY(virtualVelocity.getY() - 0.03D);
        } else {
            if (getProjectile().isDead()) {
                // gone without a hit event (removed by something else): follow the last known motion
                onProjectileHit();
                if (virtualVelocity.lengthSquared() < 0.0001D) {
                    finish();
                    return;
                }
                return;
            }
            now = getProjectile().getLocation().toVector();
        }

        Location playerLoc = getPlayer().getLocation();
        double dx = now.getX() - playerLoc.getX();
        double dz = now.getZ() - playerLoc.getZ();
        if (Math.sqrt(dx * dx + dz * dz) > MAX_DISTANCE || now.getY() < getMinHeight(world)) {
            finish();
            return;
        }

        // walk the segment last -> now so that a fast egg does not skip blocks
        Vector delta = now.clone().subtract(last);
        double length = delta.length();
        int steps = Math.max(1, (int) Math.ceil(length / STEP));
        for (int i = 1; i <= steps; i++) {
            Vector point = last.clone().add(delta.clone().multiply((double) i / steps));
            Location at = new Location(world, point.getX(), point.getY(), point.getZ());
            // keep a few blocks free around the thrower, the bridge starts in front of them
            double px = at.getX() - playerLoc.getX();
            double py = at.getY() - playerLoc.getY();
            double pz = at.getZ() - playerLoc.getZ();
            if (px * px + py * py + pz * pz <= 16.0D) continue;
            placeAround(at);
        }
        last = now;
    }

    private static int getMinHeight(World world) {
        try {
            return (Integer) World.class.getMethod("getMinHeight").invoke(world);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void placeAround(Location loc) {
        place(loc.clone().subtract(0.0D, 2.0D, 0.0D).getBlock());
        place(loc.clone().subtract(1.0D, 2.0D, 0.0D).getBlock());
        place(loc.clone().subtract(0.0D, 2.0D, 1.0D).getBlock());
    }

    /** Places one bridge block if the spot is free and not protected; otherwise it is skipped and the bridge goes on. */
    private void place(Block b) {
        if (b.getType() != Material.AIR) return;
        if (Misc.isBuildProtected(b.getLocation(), getArena())) return;
        b.setType(nms.woolMaterial());
        nms.setBlockTeamColor(b, getTeamColor());
        getArena().addPlacedBlock(b);
        Bukkit.getPluginManager().callEvent(new EggBridgeBuildEvent(getTeamColor(), getArena(), b));
        b.getWorld().playEffect(b.getLocation(), nms.eggBridge(), 3);
        Sounds.playSound("egg-bridge-block", getPlayer());
    }

    private void finish() {
        finished = true;
        EggBridge.removeEgg(projectile);
        cancel();
    }

    public void cancel() {
        finished = true;
        if (task != null) task.cancel();
    }
}
