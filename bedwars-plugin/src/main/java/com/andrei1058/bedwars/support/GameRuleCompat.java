package com.andrei1058.bedwars.support;

import com.andrei1058.bedwars.BedWars;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sets game rules by their classic names ("doDaylightCycle", ...) on every server version.
 * <p>
 * Minecraft 26.1 renamed all game rules ("advance_time", "spawn_mobs", ...) and {@link World#setGameRuleValue}
 * silently returns false for the old names, so a map would keep its day/night cycle, weather, fire spread... This
 * class first tries the classic call and falls back to the new rules through reflection (the plugin is compiled
 * against an old Bukkit API that has no {@code GameRule} class).
 */
public final class GameRuleCompat {

    private static final Map<String, String> ALIASES = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    static {
        ALIASES.put("announceadvancements", "show_advancement_messages");
        ALIASES.put("dodaylightcycle", "advance_time");
        ALIASES.put("doweathercycle", "advance_weather");
        ALIASES.put("doinsomnia", "spawn_phantoms");
        ALIASES.put("doimmediaterespawn", "immediate_respawn");
        ALIASES.put("dofiretick", "fire_spread_radius_around_player");
        ALIASES.put("domobspawning", "spawn_mobs");
        ALIASES.put("domobloot", "mob_drops");
        ALIASES.put("dotiledrops", "block_drops");
        ALIASES.put("doentitydrops", "entity_drops");
        ALIASES.put("naturalregeneration", "natural_health_regeneration");
        ALIASES.put("dopatrolspawning", "spawn_patrols");
        ALIASES.put("dotraderspawning", "spawn_wandering_traders");
        ALIASES.put("dowardenspawning", "spawn_wardens");
        ALIASES.put("dovinesspread", "spread_vines");
        ALIASES.put("dolimitedcrafting", "limited_crafting");
        ALIASES.put("disableraids", "raids");
        ALIASES.put("commandblocksenabled", "command_blocks_work");
        ALIASES.put("spawnerblocksenabled", "spawner_blocks_work");
        ALIASES.put("spawnradius", "respawn_radius");
    }

    private GameRuleCompat() {
    }

    /**
     * @param legacyName a classic rule name such as {@code doDaylightCycle} (modern names work too)
     * @param value      "true", "false" or a number
     * @return true if the rule was applied
     */
    public static boolean set(World world, String legacyName, String value) {
        try {
            if (world.setGameRuleValue(legacyName, value)) return true;
        } catch (Throwable ignored) {
            // fall through to the modern API
        }
        try {
            return setModern(world, legacyName, value);
        } catch (Throwable t) {
            warnOnce(legacyName, t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean setModern(World world, String legacyName, String value) throws Exception {
        Class<?> ruleClass = Class.forName("org.bukkit.GameRule");
        String lower = legacyName.toLowerCase(Locale.ROOT);
        String modern = ALIASES.get(lower);
        if (modern == null) modern = snake(legacyName);

        Object rule = find(ruleClass, modern);
        if (rule == null && !modern.equals(snake(legacyName))) rule = find(ruleClass, snake(legacyName));
        if (rule == null) {
            warnOnce(legacyName, "no such game rule on this server");
            return false;
        }

        Class<?> type = (Class<?>) ruleClass.getMethod("getType").invoke(rule);
        boolean flag = value.equalsIgnoreCase("true");
        Object parsed;
        if (type == Boolean.class) {
            // classic rule "disableRaids"-style inversions
            parsed = lower.equals("disableraids") ? !flag : flag;
        } else if (type == Integer.class) {
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                // doFireTick -> fire_spread_radius_around_player: off means no spread at all
                parsed = flag ? ruleClass.getMethod("getDefaultValue").invoke(rule) : Integer.valueOf(0);
            } else {
                parsed = Integer.valueOf(value);
            }
        } else {
            return false;
        }
        Method set = World.class.getMethod("setGameRule", ruleClass, Object.class);
        return (Boolean) set.invoke(world, rule, parsed);
    }

    private static Object find(Class<?> ruleClass, String name) throws Exception {
        Object[] all = (Object[]) ruleClass.getMethod("values").invoke(null);
        for (Object r : all) {
            String n = (String) ruleClass.getMethod("getName").invoke(r);
            if (n.equalsIgnoreCase(name) || n.equalsIgnoreCase("minecraft:" + name)) return r;
        }
        return null;
    }

    private static String snake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append('_').append(Character.toLowerCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }

    private static void warnOnce(String rule, String why) {
        if (WARNED.add(rule)) {
            BedWars.plugin.getLogger().warning("Could not set the game rule '" + rule + "': " + why);
        }
    }
}
