package me.midwu.guitoxaero.client.home;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.midwu.guitoxaero.client.GuitoxaeroClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

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
import java.util.Locale;
import java.util.Map;

/** Saves scanned homes per server in config/guitoxaero/homes.json. Local file only. */
public final class HomeStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, List<HomeEntry>>>() {}.getType();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("guitoxaero").resolve("homes.json");

    private static HomeStorage instance;
    private final Map<String, List<HomeEntry>> data;

    private HomeStorage(Map<String, List<HomeEntry>> data) {
        this.data = data;
    }

    public static synchronized HomeStorage get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static HomeStorage load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    Map<String, List<HomeEntry>> loaded = GSON.fromJson(reader, DATA_TYPE);
                    if (loaded != null) return new HomeStorage(new LinkedHashMap<>(loaded));
                }
            }
        } catch (IOException | RuntimeException e) {
            GuitoxaeroClient.LOGGER.warn("[GuiToXaero] could not read homes.json, starting empty", e);
        }
        return new HomeStorage(new LinkedHashMap<>());
    }

    private synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(data, DATA_TYPE, writer);
            }
        } catch (IOException e) {
            GuitoxaeroClient.LOGGER.warn("[GuiToXaero] could not write homes.json", e);
        }
    }

    /** Which server we're storing homes for. */
    public static String currentServerKey(Minecraft client) {
        ServerData server = client.getCurrentServer();
        if (server != null) return "server:" + server.ip.toLowerCase(Locale.ROOT);
        return "singleplayer";
    }

    public synchronized List<HomeEntry> getHomes(String serverKey) {
        return new ArrayList<>(data.getOrDefault(serverKey, List.of()));
    }

    public synchronized void replaceHomes(String serverKey, List<HomeEntry> homes) {
        data.put(serverKey, new ArrayList<>(homes));
        save();
    }

    public synchronized void clear(String serverKey) {
        data.remove(serverKey);
        save();
    }
}
