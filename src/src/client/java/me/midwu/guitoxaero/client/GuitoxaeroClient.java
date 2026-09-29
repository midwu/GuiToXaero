package me.midwu.guitoxaero.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DIAGNOSTIC BUILD. Only purpose: prove the mod loads, keybinds register,
 * and container screens are detected. Every step logs with the prefix
 * "[GuiToXaero]" so you can Ctrl+F the run console for it.
 */
public class GuitoxaeroClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("guitoxaero");

    public static KeyMapping SCAN_KEY;
    public static KeyMapping OPEN_MENU_KEY;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[GuiToXaero] onInitializeClient() reached - client entrypoint is running");

        try {
            KeyMapping.Category category = KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath("guitoxaero", "main"));
            LOGGER.info("[GuiToXaero] keybind category registered: {}", category);

            SCAN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.guitoxaero.scan", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
            OPEN_MENU_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.guitoxaero.open_menu", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
            LOGGER.info("[GuiToXaero] keybinds registered: scan + open_menu (look under Options > Controls > Key Binds > GuiToXaero)");
        } catch (Throwable t) {
            LOGGER.error("[GuiToXaero] FAILED to register keybinds", t);
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LOGGER.info("[GuiToXaero] joined a world");
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("[GuiToXaero] debug build loaded OK"));
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            LOGGER.info("[GuiToXaero] screen opened: {}", screen.getClass().getName());
            if (screen instanceof AbstractContainerScreen<?> container) {
                LOGGER.info("[GuiToXaero] -> container screen detected, title=\"{}\", slots={}",
                        container.getTitle().getString(), container.getMenu().slots.size());
                ScreenKeyboardEvents.afterKeyPress(screen).register((s, keyEvent) -> {
                    LOGGER.info("[GuiToXaero] key pressed inside container screen: {}", keyEvent);
                    if (SCAN_KEY != null && SCAN_KEY.matches(keyEvent)) {
                        LOGGER.info("[GuiToXaero] SCAN_KEY matched!");
                        Minecraft.getInstance().gui.getChat().addMessage(
                                Component.literal("[GuiToXaero] scan key pressed in container screen"));
                    }
                });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (OPEN_MENU_KEY != null && client.player != null) {
                while (OPEN_MENU_KEY.consumeClick()) {
                    LOGGER.info("[GuiToXaero] open_menu key pressed");
                    client.player.sendSystemMessage(Component.literal("[GuiToXaero] open_menu key pressed"));
                }
            }
        });

        LOGGER.info("[GuiToXaero] client init finished");
    }
}
