package com.andrei1058.bedwars.playmenu

/**
 * Entry point of the play menu feature.
 */
object PlayMenuModule {

    /**
     * Must be called once the languages are loaded.
     */
    @JvmStatic
    fun enable() {
        PlayMenuMessages.setupDefaults()
        com.andrei1058.bedwars.BedWars.registerEvents(PlayMenuListener())
    }
}
