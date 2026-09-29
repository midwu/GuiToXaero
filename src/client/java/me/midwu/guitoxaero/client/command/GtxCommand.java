package me.midwu.guitoxaero.client.command;

import com.mojang.brigadier.CommandDispatcher;
import me.midwu.guitoxaero.client.GuitoxaeroClient;
import me.midwu.guitoxaero.client.home.HomeEntry;
import me.midwu.guitoxaero.client.home.HomeStorage;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/** /gtx menu | list | sync | clear  (all purely client-side, nothing is sent to the server) */
public final class GtxCommand {

    private GtxCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("gtx")
                .then(ClientCommandManager.literal("menu").executes(ctx -> {
                    GuitoxaeroClient.openMenu(Minecraft.getInstance());
                    return 1;
                }))
                .then(ClientCommandManager.literal("list").executes(ctx -> {
                    List<HomeEntry> homes = HomeStorage.get().getHomes(HomeStorage.currentServerKey(Minecraft.getInstance()));
                    ctx.getSource().sendFeedback(Component.literal("[GuiToXaero] " + homes.size() + " homes"));
                    for (HomeEntry h : homes) {
                        ctx.getSource().sendFeedback(Component.literal(" - " + h.name() + " (" + h.coordsString() + ", " + h.dimensionId() + ")"));
                    }
                    return 1;
                }))
                .then(ClientCommandManager.literal("sync").executes(ctx -> {
                    List<HomeEntry> homes = HomeStorage.get().getHomes(HomeStorage.currentServerKey(Minecraft.getInstance()));
                    if (homes.isEmpty()) {
                        ctx.getSource().sendFeedback(Component.literal("[GuiToXaero] No scanned homes yet. Record a scan first."));
                        return 0;
                    }
                    ctx.getSource().sendFeedback(Component.literal("[GuiToXaero] " + GuitoxaeroClient.syncWaypoints(homes)));
                    return 1;
                }))
                .then(ClientCommandManager.literal("clear").executes(ctx -> {
                    HomeStorage.get().clear(HomeStorage.currentServerKey(Minecraft.getInstance()));
                    ctx.getSource().sendFeedback(Component.literal("[GuiToXaero] Cleared scanned homes for this server."));
                    return 1;
                }))
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.literal(
                            "[GuiToXaero] Usage: /gtx menu | list | sync | clear. Scan: open a homes GUI and press the scan key."));
                    return 1;
                }));
    }
}
