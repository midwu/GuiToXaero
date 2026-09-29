package me.midwu.guitoxaero.client.home;

import java.util.Locale;

/**
 * Maps a server's raw world name ("world", "world_nether", "world_the_end", ...)
 * to a vanilla dimension id by keyword. Edit here if your server uses other names.
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
