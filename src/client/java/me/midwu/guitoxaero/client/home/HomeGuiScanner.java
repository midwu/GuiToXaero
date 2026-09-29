package me.midwu.guitoxaero.client.home;

import me.midwu.guitoxaero.client.GuitoxaeroClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passively reads a homes GUI (World:/Location: lines in item lore).
 *
 * NEVER sends anything to the server: no slot clicks, no packets. Paging is done
 * by the player's own clicks; this only reads what is already on screen.
 *
 * Press the scan key in a container screen to start recording, browse/page
 * yourself (closing and reopening is fine), press the scan key again to stop.
 */
public final class HomeGuiScanner {

    private static final Pattern WORLD_LINE = Pattern.compile("(?i)^\\s*world\\s*:\\s*(.+?)\\s*$");
    private static final Pattern LOCATION_LINE =
            Pattern.compile("(?i)^\\s*location\\s*:\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*$");

    private static final int AUTO_STOP_AFTER_TICKS = 20 * 30;

    private static boolean active = false;
    private static String lastPageSignature = null;
    private static int pagesSeen = 0;
    private static int ticksWithoutScreen = 0;
    private static final Map<String, HomeEntry> collected = new LinkedHashMap<>();

    private HomeGuiScanner() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register((s, keyEvent) -> {
                if (GuitoxaeroClient.SCAN_KEY != null && GuitoxaeroClient.SCAN_KEY.matches(keyEvent)) {
                    toggleScan(containerScreen);
                }
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(HomeGuiScanner::onClientTick);
    }

    private static void toggleScan(AbstractContainerScreen<?> screen) {
        if (active) {
            finishScan(Minecraft.getInstance());
            return;
        }
        active = true;
        pagesSeen = 0;
        ticksWithoutScreen = 0;
        lastPageSignature = null;
        collected.clear();
        GuitoxaeroClient.LOGGER.info("[GuiToXaero] recording started on \"{}\"", screen.getTitle().getString());
        feedback("Recording started on \"" + screen.getTitle().getString() + "\". Browse it yourself (closing/reopening "
                + "is fine). Press the scan key again when done.");
        readCurrentPage(screen);
    }

    private static void onClientTick(Minecraft client) {
        if (!active) return;
        if (!(client.screen instanceof AbstractContainerScreen<?> screen)) {
            ticksWithoutScreen++;
            if (ticksWithoutScreen > AUTO_STOP_AFTER_TICKS) {
                feedback("No homes GUI open for a while - stopping the recording automatically.");
                finishScan(client);
            }
            return;
        }
        ticksWithoutScreen = 0;
        readCurrentPage(screen);
    }

    /** Read-only: looks at slot contents the client already has. */
    private static void readCurrentPage(AbstractContainerScreen<?> screen) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        StringBuilder signature = new StringBuilder();
        int newHomes = 0;

        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == player.getInventory()) continue; // skip the player's own inventory
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String plainName = stack.getHoverName().getString();
            signature.append(slot.getContainerSlot()).append(':').append(plainName).append(';');

            HomeEntry entry = tryParseHome(stack, plainName);
            if (entry != null) {
                String key = entry.name().toLowerCase(Locale.ROOT);
                if (!collected.containsKey(key)) newHomes++;
                collected.put(key, entry);
            }
        }

        String sig = signature.toString();
        if (sig.equals(lastPageSignature)) return; // nothing changed
        lastPageSignature = sig;
        pagesSeen++;
        GuitoxaeroClient.LOGGER.info("[GuiToXaero] captured page {}: {} new homes, {} total", pagesSeen, newHomes, collected.size());
        if (pagesSeen > 1) {
            feedback("Captured page " + pagesSeen + " (" + newHomes + " new, " + collected.size() + " total so far).");
        }
    }

    private static HomeEntry tryParseHome(ItemStack stack, String plainName) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;

        String world = null;
        Integer x = null, y = null, z = null;
        for (Component line : lore.lines()) {
            String plain = line.getString();
            Matcher wm = WORLD_LINE.matcher(plain);
            if (wm.matches()) {
                world = wm.group(1);
                continue;
            }
            Matcher lm = LOCATION_LINE.matcher(plain);
            if (lm.matches()) {
                x = Integer.parseInt(lm.group(1));
                y = Integer.parseInt(lm.group(2));
                z = Integer.parseInt(lm.group(3));
            }
        }
        if (world == null || x == null) return null;

        String name = plainName.isBlank() ? ("home_" + x + "_" + z) : plainName;
        return new HomeEntry(name, world, DimensionGuesser.guessDimensionId(world), x, y, z);
    }

    private static void finishScan(Minecraft client) {
        active = false;
        List<HomeEntry> homes = new ArrayList<>(collected.values());
        collected.clear();

        if (homes.isEmpty()) {
            feedback("Stopped recording - no homes captured (need items with \"World:\" and \"Location:\" lore).");
            return;
        }
        HomeStorage.get().replaceHomes(HomeStorage.currentServerKey(client), homes);
        GuitoxaeroClient.LOGGER.info("[GuiToXaero] recording stopped: {} homes saved", homes.size());
        feedback("Stopped. Captured " + homes.size() + " homes across " + pagesSeen + " page(s).");
        feedback("Run /gtx sync to create/update Xaero waypoints, or /gtx menu to browse.");
    }

    private static void feedback(String message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal("[GuiToXaero] " + message), false);
    }
}
