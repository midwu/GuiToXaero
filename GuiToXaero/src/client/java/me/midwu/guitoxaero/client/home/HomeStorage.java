package me.midwu.guitoxaero.client.home;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores scanned homes on disk, keyed by "server". This means every server
 * you play on keeps its own independent home list, and re-scanning one
 * server never touches another server's data.
 */
public final class HomeStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, List<HomeEntry>>>() {}.getType();

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("guitoxaero").resolve("homes.json");

    private final Map<String, List<HomeEntry>> data;

    private static HomeStorage instance;

    private HomeStorage(Map<String, List<HomeEntry>> data) {
        this.data = data;
    }

    public static synchronized HomeStorage get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static HomeStorage load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    Map<String, List<HomeEntry>> loaded = GSON.fromJson(reader, DATA_TYPE);
                    if (loaded != null) {
                        return new HomeStorage(new LinkedHashMap<>(loaded));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // Corrupt or unreadable file - start fresh rather than crashing the game.
        }
        return new HomeStorage(new LinkedHashMap<>());
    }

    private synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(data, DATA_TYPE, writer);
            }
        } catch (IOException ignored) {
            // Non-fatal: worst case, homes just get re-scanned next time.
        }
    }

    /** A stable key identifying "which server" we're storing homes for. */
    public static String currentServerKey(MinecraftClient client) {
        ServerInfo entry = client.getCurrentServerEntry();
        if (entry != null) {
            return "server:" + entry.address.toLowerCase(java.util.Locale.ROOT);
        }
        if (client.isInSingleplayer() && client.getServer() != null) {
            return "singleplayer:" + client.getServer().getSaveProperties().getLevelName();
        }
        return "unknown";
    }

    public synchronized List<HomeEntry> getHomes(String serverKey) {
        return new ArrayList<>(data.getOrDefault(serverKey, List.of()));
    }

    /** Replaces the stored homes for a server with a freshly-scanned list, then saves to disk. */
    public synchronized void replaceHomes(String serverKey, List<HomeEntry> homes) {
        data.put(serverKey, new ArrayList<>(homes));
        save();
    }

    public synchronized void clear(String serverKey) {
        data.remove(serverKey);
        save();
    }
}
