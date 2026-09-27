package me.midwu.guitoxaero.client.home;

import java.util.Locale;

/**
 * Servers usually run Bukkit/Spigot/Paper with the default world names
 * ("world", "world_nether", "world_the_end") or a close variant of them
 * (via Multiverse, etc). We don't have a reliable way to ask the server
 * which vanilla dimension a given world folder actually is (the home GUI
 * only ever gives us the world's plain name), so this maps by keyword,
 * which covers the overwhelming majority of servers.
 *
 * If your server uses completely custom world names that don't include
 * "nether" or "end", homes there will be treated as overworld - adjust the
 * keyword lists below for your server if needed.
 */
public final class DimensionGuesser {

    private DimensionGuesser() {}

    public static String guessDimensionId(String rawWorldName) {
        String w = rawWorldName.toLowerCase(Locale.ROOT);
        if (w.contains("the_end") || w.contains("theend") || w.endsWith("_end") || w.contains(" end")) {
            return "minecraft:the_end";
        }
        if (w.contains("nether")) {
            return "minecraft:the_nether";
        }
        return "minecraft:overworld";
    }
}
