package com.andrei1058.bedwars.support.version.v1_21_R1.sidebar

import io.papermc.paper.scoreboard.numbers.NumberFormat
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.scoreboard.Score

/**
 * Paper-only: replaces the red score digits on the right of the sidebar. Never touched on plain Spigot.
 */
internal object PaperNumbers {

    fun apply(score: Score, text: String?) {
        if (text.isNullOrEmpty()) {
            score.numberFormat(NumberFormat.blank())
        } else {
            score.numberFormat(NumberFormat.fixed(LegacyComponentSerializer.legacySection().deserialize(text)))
        }
    }
}
