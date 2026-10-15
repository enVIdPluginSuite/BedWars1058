package com.andrei1058.bedwars.playmenu

import com.andrei1058.bedwars.api.language.Language

/**
 * Texts of the play menu. The defaults are written into every language file that does not have them yet,
 * so server owners can translate or restyle them like any other message.
 */
object PlayMenuMessages {

    private const val PATH = "play-menu."

    const val TITLE = PATH + "title"
    const val RANDOM_NAME = PATH + "random.name"
    const val RANDOM_LORE = PATH + "random.lore"
    const val SELECTOR_NAME = PATH + "selector.name"
    const val SELECTOR_LORE = PATH + "selector.lore"
    const val CLOSE_NAME = PATH + "close.name"
    const val CLOSE_LORE = PATH + "close.lore"
    const val RECONNECT_NAME = PATH + "reconnect.name"
    const val RECONNECT_LORE_AVAILABLE = PATH + "reconnect.lore-available"
    const val RECONNECT_LORE_UNAVAILABLE = PATH + "reconnect.lore-unavailable"

    /**
     * Add the missing messages to every loaded language. Call once the languages are loaded.
     */
    fun setupDefaults() {
        for (language in Language.getLanguages()) {
            addDefault(language, TITLE, "&8Play Bed Wars")
            addDefault(language, RANDOM_NAME, "&aRandom Arena")
            addDefault(language, RANDOM_LORE, listOf("&7Join a random arena that is", "&7waiting for players.", "", "&7Group: &e{group}"))
            addDefault(language, SELECTOR_NAME, "&aArena Selector")
            addDefault(language, SELECTOR_LORE, listOf("&7Browse the arenas and", "&7pick the one you like."))
            addDefault(language, CLOSE_NAME, "&cClose")
            addDefault(language, CLOSE_LORE, listOf("&7Close this menu."))
            addDefault(language, RECONNECT_NAME, "&6Reconnect")
            addDefault(language, RECONNECT_LORE_AVAILABLE, listOf("&7Return to your game on", "&e{arena}&7."))
            addDefault(language, RECONNECT_LORE_UNAVAILABLE, listOf("&7You have no game", "&7to reconnect to."))
        }
    }

    private fun addDefault(language: Language, path: String, value: Any) {
        if (!language.exists(path)) {
            language.set(path, value)
        }
    }
}
