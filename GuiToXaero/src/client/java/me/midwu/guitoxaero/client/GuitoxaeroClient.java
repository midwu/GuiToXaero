package me.midwu.guitoxaero.client;

import com.mojang.blaze3d.platform.InputUtil;
import me.midwu.guitoxaero.client.command.GtxCommand;
import me.midwu.guitoxaero.client.gui.HomesMenuScreen;
import me.midwu.guitoxaero.client.home.HomeGuiScanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GuitoxaeroClient implements ClientModInitializer {

    public static final Logger LOGGER = LogManager.getLogger("guitoxaero");

    /** Pressed while a homes GUI is open to scan the current page (and auto-advance pages). */
    public static KeyBinding SCAN_KEY;

    /** Opens the GuiToXaero homes menu from anywhere (doesn't need a GUI open). */
    public static KeyBinding OPEN_MENU_KEY;

    @Override
    public void onInitializeClient() {
        SCAN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.guitoxaero.scan",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(), // unbound by default - bind it in Controls > GuiToXaero
                "category.guitoxaero"
        ));

        OPEN_MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.guitoxaero.open_menu",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                "category.guitoxaero"
        ));

        HomeGuiScanner.register();

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU_KEY.wasPressed()) {
                openMenu(client);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                GtxCommand.register(dispatcher));

        LOGGER.info("GuiToXaero client setup complete!");
    }

    public static void openMenu(MinecraftClient client) {
        client.setScreen(new HomesMenuScreen());
    }
}
