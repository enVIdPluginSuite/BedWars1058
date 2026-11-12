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

package com.andrei1058.bedwars.support.party;

import com.andrei1058.bedwars.api.party.Party;
import com.andrei1058.bedwars.party.PartyService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Built-in party provider. The rules live in {@link PartyService}; this adapter only exposes them through the
 * {@link Party} API the arenas use.
 */
public class Internal implements Party {

    @Override
    public boolean hasParty(Player p) {
        return PartyService.INSTANCE.partyOf(p) != null;
    }

    @Override
    public int partySize(Player p) {
        PartyService.Party party = PartyService.INSTANCE.partyOf(p);
        return party == null ? 0 : party.getMembers().size();
    }

    @Override
    public boolean isOwner(Player p) {
        return PartyService.INSTANCE.isLeader(p);
    }

    @Override
    public List<Player> getMembers(Player owner) {
        PartyService.Party party = PartyService.INSTANCE.partyOf(owner);
        return party == null ? null : new ArrayList<>(party.online());
    }

    @Override
    public void createParty(Player owner, Player... members) {
        PartyService.INSTANCE.create(owner, members);
    }

    @Override
    public void addMember(Player owner, Player member) {
        PartyService.Party party = PartyService.INSTANCE.partyOf(owner);
        if (party != null && member != null) PartyService.INSTANCE.join(party, member, false);
    }

    @Override
    public void removeFromParty(Player member) {
        PartyService.INSTANCE.leave(member, true);
    }

    @Override
    public void disband(Player owner) {
        PartyService.Party party = PartyService.INSTANCE.partyOf(owner);
        if (party != null) PartyService.INSTANCE.disbandParty(party);
    }

    @Override
    public boolean isMember(Player owner, Player check) {
        PartyService.Party party = PartyService.INSTANCE.partyOf(owner);
        return party != null && party.getMembers().contains(check.getUniqueId());
    }

    @Override
    public void removePlayer(Player owner, Player target) {
        PartyService.INSTANCE.kick(owner, target.getName());
    }

    @Override
    public Player getOwner(Player member) {
        PartyService.Party party = PartyService.INSTANCE.partyOf(member);
        if (party == null) return null;
        UUID leader = party.getLeader();
        return org.bukkit.Bukkit.getPlayer(leader);
    }

    @Override
    public void promote(@NotNull Player owner, @NotNull Player target) {
        PartyService.INSTANCE.transfer(owner, target.getName());
    }

    @Override
    public boolean isInternal() {
        return true;
    }
}
