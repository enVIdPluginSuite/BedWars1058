package com.andrei1058.bedwars.teamselector.teamselector

import com.andrei1058.bedwars.BedWars
import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.team.ITeam
import com.andrei1058.bedwars.api.arena.team.ITeamAssigner
import com.andrei1058.bedwars.api.events.gameplay.TeamAssignEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Gives every player a team when the game starts, honouring parties and the teams players picked in the selector.
 */
class TeamSelectorAssigner : ITeamAssigner {

    override fun assignTeams(arena: IArena) {
        // these are rebuilt for every game
        val playerGroups = ArrayList<PlayerGroup>()
        val playersAddedToATeam = HashSet<UUID>()
        val teamsWithSpace = ArrayList(arena.teams)

        playerGroups.addAll(createPartyGroups(arena))

        // groups from the teams players picked
        val preferences = TeamManager.getArena(arena)
        for (preferredTeam in preferences.selections.values.distinct()) {
            val group = PlayerGroup(arena, preferredTeam)
            // the amount of players in that case cannot be bigger than max in team, so nothing has to be split
            preferences.getMembers(preferredTeam).forEach { group.addPlayer(it) }
            playerGroups.add(group)
        }

        playerGroups.sort()

        // order teams by the least requested, to match the group order
        if (preferences.selections.isNotEmpty()) {
            teamsWithSpace.sortBy { preferences.getMembers(it).size }
        }

        for (group in playerGroups) {
            if (group.getMembers().isEmpty()) continue
            if (group.preference == null) {
                assignParty(arena, group, teamsWithSpace, playersAddedToATeam)
            } else {
                assignPreferenceGroup(arena, group, group.preference, teamsWithSpace, playersAddedToATeam)
            }
        }

        assignRemainingPlayers(arena, teamsWithSpace, playersAddedToATeam)
    }

    /**
     * Create groups from parties. Parties bigger than a team are split.
     */
    private fun createPartyGroups(arena: IArena): List<PlayerGroup> {
        val groups = ArrayList<PlayerGroup>()
        // all the members of a handled party are skipped, since several members may be recognized as owner
        val alreadyHandled = HashSet<UUID>()

        for (player in arena.players) {
            if (player.uniqueId in alreadyHandled || !BedWars.getParty().isOwner(player)) continue

            val partyMembers = ArrayDeque<Player>()
            for (member in BedWars.getParty().getMembers(player)) {
                // only members that are in this very arena
                if (arena.isPlayer(member)) {
                    partyMembers.add(member)
                    alreadyHandled.add(member.uniqueId)
                }
            }
            // some parties do not list the owner among the members
            if (player !in partyMembers) {
                partyMembers.add(player)
                alreadyHandled.add(player.uniqueId)
            }

            if (partyMembers.size < 2) continue

            var group: PlayerGroup? = null
            while (partyMembers.isNotEmpty()) {
                if (group == null || group.getMembers().size == arena.maxInTeam) {
                    group = PlayerGroup(arena, null)
                    groups.add(group)
                }
                group.addPlayer(partyMembers.removeFirst())
            }
        }
        return groups
    }

    /**
     * Put a whole party in the first team that has enough free slots.
     */
    private fun assignParty(
        arena: IArena, party: PlayerGroup, teamsWithSpace: MutableList<ITeam>, assigned: MutableSet<UUID>
    ) {
        val targetTeam = teamsWithSpace.firstOrNull {
            arena.maxInTeam - it.members.size >= party.getMembers().size
        } ?: return

        for (player in party.getMembers()) {
            addToTeam(arena, player, targetTeam)
            assigned.add(player.uniqueId)
        }
        if (targetTeam.members.size == arena.maxInTeam) {
            teamsWithSpace.remove(targetTeam)
        }
    }

    /**
     * Put the players that picked a team in it, as long as there are free slots left.
     * Groups that can fill a whole team are handled first, so a team never ends up compromised by a smaller group.
     */
    private fun assignPreferenceGroup(
        arena: IArena, group: PlayerGroup, team: ITeam, teamsWithSpace: MutableList<ITeam>, assigned: MutableSet<UUID>
    ) {
        for (player in group.getMembers()) {
            if (team.members.size < arena.maxInTeam) {
                addToTeam(arena, player, team)
                assigned.add(player.uniqueId)
            }
        }
        if (team.members.size == arena.maxInTeam) {
            teamsWithSpace.remove(team)
        }
    }

    /**
     * Players without a party or a choice, or whose choice could not be honoured, take any team with free space.
     */
    private fun assignRemainingPlayers(arena: IArena, teamsWithSpace: List<ITeam>, assigned: Set<UUID>) {
        for (player in arena.players) {
            if (player.uniqueId in assigned) continue

            val team = teamsWithSpace.firstOrNull { it.members.size < arena.maxInTeam }
            if (team == null) {
                player.kickPlayer("That was unexpected: you haven't been assigned to a team!")
            } else {
                team.addPlayers(player)
            }
        }
    }

    private fun addToTeam(arena: IArena, player: Player, team: ITeam) {
        team.addPlayers(player)
        Bukkit.getPluginManager().callEvent(TeamAssignEvent(player, team, arena))
    }
}
