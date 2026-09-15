package com.andrei1058.bedwars.teamselector.teamselector

import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.team.ITeam
import org.bukkit.entity.Player

/**
 * Players that must end up in the same team: either a party (no preference) or the players who picked the same team.
 */
class PlayerGroup(private val arena: IArena, val preference: ITeam?) : Comparable<PlayerGroup> {

    private val members: MutableSet<Player> = LinkedHashSet()

    /**
     * Add a player to this group if the team size limit was not reached.
     */
    fun addPlayer(player: Player) {
        if (members.size == arena.maxInTeam) return
        members.add(player)
    }

    /**
     * A party of one player has nobody to team up with, so it counts as empty.
     */
    fun getMembers(): Set<Player> {
        if (preference != null) return members
        return if (members.size > 1) members else emptySet()
    }

    private fun canFillATeam(): Boolean = getMembers().size == arena.maxInTeam

    /**
     * Sort order used by the assigner: groups that come later in the order are placed first.
     */
    override fun compareTo(other: PlayerGroup): Int {
        if (preference == null) {
            // a party versus another party: bigger parties first
            if (other.preference == null) {
                return getMembers().size.compareTo(other.getMembers().size)
            }
            // a party versus a preference group: the preference group goes first only if it can fill a whole team
            return if (other.getMembers().size == arena.maxInTeam) -1 else 1
        }

        // a preference group that can fill a whole team always goes first
        if (canFillATeam()) return 1
        // otherwise parties go first
        return -1
    }
}
