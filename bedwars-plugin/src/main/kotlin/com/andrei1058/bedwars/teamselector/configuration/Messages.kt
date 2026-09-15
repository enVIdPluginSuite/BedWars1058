package com.andrei1058.bedwars.teamselector.configuration

import com.andrei1058.bedwars.api.language.Language

/**
 * Message paths of the team selector and their default texts.
 * The paths are unchanged from the standalone addon, so translated language files keep working.
 */
object Messages {

    const val PATH = "addons.team-selector."
    const val GUI_NAME = PATH + "inventory-name"
    const val SELECTOR_NAME = PATH + "selector-name"
    const val SELECTOR_LORE = PATH + "selector-lore"
    const val CHOICE_NAME = PATH + "choice.name"
    const val CHOICE_LORE = PATH + "choice.lore"
    const val SWITCH_DISABLED = PATH + "switch-disabled"
    const val TEAM_JOIN = PATH + "team-join"
    const val TEAM_FULL = PATH + "team-full"
    const val TEAM_NOT_BALANCED = PATH + "teams-not-balanced"
    const val PARTY_DENIED = PATH + "party-deny"
    const val CANT_JOIN_WHILE_STARTING = PATH + "cant-join-while-starting"
    const val ALREADY_IN_TEAM = PATH + "already-in-team"

    /**
     * Write the default messages into every language file that does not have them yet.
     */
    @JvmStatic
    fun setupMessages() {
        for (language in Language.getLanguages()) {
            addDefault(language, GUI_NAME, "&8Team Selector", "&8Alege o Echipa")
            addDefault(language, SELECTOR_NAME, "&9Team Selector", "&9Alege o Echipa")
            addDefault(
                language, SELECTOR_LORE,
                listOf("&7Right-Click to to open!"), listOf("&7Deschide cu click-dreapta!")
            )
            addDefault(
                language, CHOICE_LORE,
                listOf("", "&7&o{members}", "", "&eClick to join!"), listOf("", "&7&o{members}", "", "&eIntra cu Click!")
            )
            addDefault(
                language, CHOICE_NAME,
                "{color}{team} &f({selected}&7/&f{total})", "{color}{team} &f({selected}&7/&f{total})"
            )
            addDefault(
                language, SWITCH_DISABLED,
                "{prefix}&cYou cannot change your team!", "{prefix}&cPoti alege echipa o singura data!"
            )
            addDefault(
                language, TEAM_JOIN,
                "{prefix}&eYou joined the {color}{team} &eteam!", "{prefix}&eTe-ai alaturat echipei {color}{team}&e!"
            )
            addDefault(
                language, TEAM_FULL,
                "{prefix}{color}{team} &c Team is full!", "{prefix}&cEchipa {color}{team} &c este plina!"
            )
            addDefault(
                language, TEAM_NOT_BALANCED,
                "{prefix}&cTeams are not balanced! Try joining another team!",
                "{prefix}&cEchipele nu sunt echilibrate! Incearca alta!"
            )
            addDefault(
                language, PARTY_DENIED,
                "{prefix}&cYou can't choose your team because you're in a party!",
                "{prefix}&cNu poti alege o echipa pentru că te afli intr-un party!"
            )
            addDefault(
                language, CANT_JOIN_WHILE_STARTING,
                "{prefix}&cYou cannot join this team at this moment. The game is starting!",
                "{prefix}&cNu poti intra in aceasta echipa chiar acum. Incepe jocul!"
            )
            addDefault(
                language, ALREADY_IN_TEAM,
                "{prefix}&cYou are already in this team!", "{prefix}&cSunteți deja în această echipă"
            )
        }
    }

    private fun addDefault(language: Language, path: String, english: Any, romanian: Any) {
        if (!language.exists(path)) {
            language.set(path, if (language.iso == "ro") romanian else english)
        }
    }
}
