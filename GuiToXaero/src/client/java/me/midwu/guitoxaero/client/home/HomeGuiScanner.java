package me.midwu.guitoxaero.client.home;

import me.midwu.guitoxaero.client.GuitoxaeroClient;
import me.midwu.guitoxaero.client.xaero.XaeroWaypointBridge;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a server's home-listing chest GUI: extracts every home's name,
 * world and coordinates from item lore.
 *
 * <p><b>This class never sends anything to the server.</b> It only reads
 * the slot contents Minecraft already gave the client (the same data the
 * screen is rendering) and never calls {@code clickSlot} or any other
 * network-facing method. Turning pages, clicking items, everything that
 * talks to the server is always done by the player's own mouse clicks -
 * exactly like it would be with the mod not installed. The mod just watches
 * what's already on screen and remembers it.
 *
 * <p>Workflow: press the scan key once to start "recording" while a homes
 * GUI is open, then manually click through the pages yourself as normal.
 * Every time the page contents change (because you clicked "next page"),
 * this passively captures the new page's homes and merges them into what
 * it already has. Press the scan key again (or just close the GUI) to
 * finish and sync everything to Xaero.
 */
public final class HomeGuiScanner {

    private static final Pattern WORLD_LINE = Pattern.compile("(?i)^\\s*world\\s*:\\s*(.+?)\\s*$");
    private static final Pattern LOCATION_LINE =
            Pattern.compile("(?i)^\\s*location\\s*:\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*$");

    private static boolean active = false;
    private static String lastPageSignature = null;
    private static int pagesSeen = 0;
    private static final Map<String, HomeEntry> collected = new LinkedHashMap<>();

    private HomeGuiScanner() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((screen, client, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof HandledScreen<?> handledScreen)) return;

            ScreenKeyboardEvents.afterKeyPress(screen).register((s, key, scancode, modifiers) -> {
                if (GuitoxaeroClient.SCAN_KEY.matchesKey(key, scancode)) {
                    toggleScan(handledScreen);
                }
            });

            // If the GUI closes while we were recording, finish up with whatever was seen.
            ScreenEvents.remove(screen).register(s -> {
                if (active) finishScan(MinecraftClient.getInstance());
            });
        });

        // Purely observational: on every client tick, if we're recording, check whether the
        // page's contents changed (i.e. the player just clicked to the next page themselves)
        // and if so, read what's now showing. No packets are ever sent from here.
        ClientTickEvents.END_CLIENT_TICK.register(HomeGuiScanner::onClientTick);
    }

    public static boolean isActive() {
        return active;
    }

    private static void toggleScan(HandledScreen<?> screen) {
        if (active) {
            finishScan(MinecraftClient.getInstance());
        } else {
            active = true;
            pagesSeen = 0;
            lastPageSignature = null;
            collected.clear();
            feedback("Recording started on \"" + screen.getTitle().getString()
                    + "\". Click through the pages yourself; press the scan key again when you're done.");
            readCurrentPage(screen);
        }
    }

    private static void onClientTick(MinecraftClient client) {
        if (!active) return;
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
            finishScan(client);
            return;
        }
        readCurrentPage(screen);
    }

    /** Reads whatever is currently showing in the GUI. Read-only: no clicks, no packets. */
    private static void readCurrentPage(HandledScreen<?> screen) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        List<Slot> slots = screen.getScreenHandler().slots;
        StringBuilder signature = new StringBuilder();
        int newHomesThisPage = 0;

        for (Slot slot : slots) {
            // Skip the player's own inventory/hotbar slots shown at the bottom of a chest GUI.
            if (slot.inventory == player.getInventory()) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            String plainName = stack.getName().getString();
            signature.append(slot.getIndex()).append(':').append(plainName).append(';');

            HomeEntry entry = tryParseHome(stack, plainName);
            if (entry != null) {
                String key = entry.name().toLowerCase(Locale.ROOT);
                if (!collected.containsKey(key)) newHomesThisPage++;
                collected.put(key, entry);
            }
        }

        String sig = signature.toString();
        if (sig.equals(lastPageSignature)) {
            return; // Nothing changed since the last read - the player hasn't clicked anywhere yet.
        }
        lastPageSignature = sig;
        pagesSeen++;

        if (pagesSeen > 1) {
            feedback("Captured page " + pagesSeen + " (" + newHomesThisPage + " new, "
                    + collected.size() + " total so far).");
        }
    }

    private static HomeEntry tryParseHome(ItemStack stack, String plainName) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        String world = null;
        Integer x = null, y = null, z = null;

        for (Text line : lore.lines()) {
            String plain = line.getString();
            Matcher worldMatcher = WORLD_LINE.matcher(plain);
            if (worldMatcher.matches()) {
                world = worldMatcher.group(1);
                continue;
            }
            Matcher locMatcher = LOCATION_LINE.matcher(plain);
            if (locMatcher.matches()) {
                x = Integer.parseInt(locMatcher.group(1));
                y = Integer.parseInt(locMatcher.group(2));
                z = Integer.parseInt(locMatcher.group(3));
            }
        }

        if (world == null || x == null) return null;

        String name = plainName.isBlank() ? ("home_" + x + "_" + z) : plainName;
        String dimensionId = DimensionGuesser.guessDimensionId(world);
        return new HomeEntry(name, world, dimensionId, x, y, z);
    }

    private static void finishScan(MinecraftClient client) {
        active = false;
        List<HomeEntry> homes = new ArrayList<>(collected.values());
        collected.clear();

        if (homes.isEmpty()) {
            feedback("Stopped recording - no homes were captured. Make sure the page had items with "
                    + "\"World:\" / \"Location:\" lore while recording was on.");
            return;
        }

        String serverKey = HomeStorage.currentServerKey(client);
        HomeStorage.get().replaceHomes(serverKey, homes);

        feedback("Stopped recording. Captured " + homes.size() + " homes across " + pagesSeen + " page(s).");
        feedback("Run /gtx sync when you're ready to create/update Xaero waypoints for them.");
        feedback("Use /gtx menu (or your menu key) to browse and teleport.");
    }

    private static void feedback(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(Text.literal("[GuiToXaero] " + message));
        }
    }
}
