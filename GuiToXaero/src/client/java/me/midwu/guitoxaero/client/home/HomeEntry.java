package me.midwu.guitoxaero.client.home;

import java.util.Objects;

/**
 * One home parsed out of the server's home-listing GUI.
 *
 * @param name       the home's display name, e.g. "base", "brew"
 * @param rawWorld   the raw "World: xxx" text as printed by the server (e.g. "world_the_end")
 * @param dimensionId the guessed vanilla dimension id, e.g. "minecraft:the_nether"
 * @param x          block X
 * @param y          block Y
 * @param z          block Z
 */
public record HomeEntry(String name, String rawWorld, String dimensionId, int x, int y, int z) {

    public HomeEntry {
        Objects.requireNonNull(name);
        Objects.requireNonNull(rawWorld);
        Objects.requireNonNull(dimensionId);
    }

    public String coordsString() {
        return x + ", " + y + ", " + z;
    }
}
