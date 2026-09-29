package me.midwu.guitoxaero.client.home;

import java.util.Objects;

/** One home parsed from the server's home-listing GUI. */
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
