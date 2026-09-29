package me.midwu.guitoxaero.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.midwu.guitoxaero.client.command.GtxCommand;
import me.midwu.guitoxaero.client.gui.HomesMenuScreen;
import me.midwu.guitoxaero.client.home.HomeEntry;
import me.midwu.guitoxaero.client.home.HomeGuiScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class GuitoxaeroClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("guitoxaero");

    /** Press inside a homes GUI to start/stop recording. Unbound by default. */
    public static KeyMapping SCAN_KEY;
    /** Opens the homes menu. Unbound by default. */
    public static KeyMapping OPEN_MENU_KEY;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[GuiToXaero] onInitializeClient() reached");

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("guitoxaero", "main"));
        SCAN_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.guitoxaero.scan", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        OPEN_MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.guitoxaero.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        HomeGuiScanner.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU_KEY.consumeClick()) {
                openMenu(client);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> GtxCommand.register(dispatcher));

        LOGGER.info("[GuiToXaero] client init finished (Xaero present: {})", xaeroPresent());
    }

    public static void openMenu(Minecraft client) {
        client.setScreen(new HomesMenuScreen());
    }

    /** Checked by class lookup so the mod still runs (without waypoint sync) if Xaero is missing. */
    public static boolean xaeroPresent() {
        try {
            Class.forName("xaero.hud.minimap.BuiltInHudModules", false, GuitoxaeroClient.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String syncWaypoints(List<HomeEntry> homes) {
        if (!xaeroPresent()) {
            return "Xaero's Minimap is not loaded, so no waypoints were created.";
        }
        try {
            return me.midwu.guitoxaero.client.xaero.XaeroWaypointBridge.syncHomes(homes);
        } catch (Throwable t) {
            LOGGER.error("[GuiToXaero] waypoint sync failed", t);
            return "Waypoint sync failed (see log): " + t;
        }
    }
}
