package com.andrei1058.bedwars.support.version.v1_21_R1.sidebar;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.scoreboard.Score;

/**
 * Paper-only: replaces the red score digits on the right of the sidebar. Never touched on plain Spigot.
 */
final class PaperNumbers {

    private PaperNumbers() {
    }

    static void apply(Score score, String text) {
        if (text == null || text.isEmpty()) {
            score.numberFormat(NumberFormat.blank());
        } else {
            score.numberFormat(NumberFormat.fixed(LegacyComponentSerializer.legacySection().deserialize(text)));
        }
    }
}
