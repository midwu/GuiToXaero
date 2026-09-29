package me.midwu.guitoxaero.client.home;

import me.midwu.guitoxaero.client.GuitoxaeroClient;
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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Press-only page capture: every press of the scan key reads exactly the page currently
 * on screen (World:/Location: lore) and merges it into the saved home list for this
 * server, then reports what it found in chat. Nothing is automatic - you press the key
 * again on every page, including the first one.
 *
 * NEVER sends anything to the server: no slot clicks, no packets, no polling between
 * presses. Paging is entirely done by the player's own clicks; this only reads what's
 * already on screen at the moment the key is pressed.
 */
public final class HomeGuiScanner {

    private static final Pattern WORLD_LINE = Pattern.compile("(?i)^\\s*world\\s*:\\s*(.+?)\\s*$");
    private static final Pattern LOCATION_LINE =
            Pattern.compile("(?i)^\\s*location\\s*:\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*$");

    private HomeGuiScanner() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register((s, keyEvent) -> {
                if (GuitoxaeroClient.SCAN_KEY != null && GuitoxaeroClient.SCAN_KEY.matches(keyEvent)) {
                    captureCurrentPage(containerScreen);
                }
            });
        });
    }

    /** Reads whatever is on screen right now and merges it in. Called once per key press. */
    private static void captureCurrentPage(AbstractContainerScreen<?> screen) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        List<HomeEntry> pageHomes = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container == player.getInventory()) continue; // skip the player's own inventory
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            String plainName = stack.getHoverName().getString();
            HomeEntry entry = tryParseHome(stack, plainName);
            if (entry != null) pageHomes.add(entry);
        }

        if (pageHomes.isEmpty()) {
            feedback("No homes found on this page (need items with \"World:\" and \"Location:\" lore).");
            return;
        }

        String serverKey = HomeStorage.currentServerKey(Minecraft.getInstance());
        HomeStorage.MergeResult result = HomeStorage.get().mergeHomes(serverKey, pageHomes);
        int total = HomeStorage.get().getHomes(serverKey).size();

        GuitoxaeroClient.LOGGER.info("[GuiToXaero] captured page: {} new, {} updated, {} unchanged, {} total",
                result.added(), result.updated(), result.unchanged(), total);
        feedback("Captured this page: " + result.added() + " new, " + result.updated() + " updated, "
                + result.unchanged() + " already saved. " + total + " homes saved for this server.");
        feedback("Turn the page and press the scan key again, or run /gtx sync / /gtx menu when done.");
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

    private static void feedback(String message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal("[GuiToXaero] " + message), false);
    }
}