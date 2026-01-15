package com.andrei1058.bedwars.support.version.v1_20_R3

import com.andrei1058.bedwars.support.version.v1_20_R2.v1_20_R2
import org.bukkit.plugin.Plugin

/**
 * Version specific fixes for this server revision go here. Everything else is inherited from [v1_20_R2],
 * so fixes made in an older revision propagate down to this one.
 */
@Suppress("ClassName", "unused")
open class v1_20_R3(plugin: Plugin, name: String) : v1_20_R2(plugin, name) {

    override fun getVersion(): Int {
        // impacts on the sidebar
        // experimental score placeholders
        return 10
    }
}
