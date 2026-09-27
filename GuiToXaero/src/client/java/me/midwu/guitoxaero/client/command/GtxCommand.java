package me.midwu.guitoxaero.client.command;

import com.mojang.brigadier.CommandDispatcher;
import me.midwu.guitoxaero.client.GuitoxaeroClient;
import me.midwu.guitoxaero.client.home.HomeEntry;
import me.midwu.guitoxaero.client.home.HomeStorage;
import me.midwu.guitoxaero.client.xaero.XaeroWaypointBridge;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;

/**
 * /gtx menu   - opens the homes menu screen
 * /gtx list   - prints known homes to chat
 * /gtx sync   - creates/updates local Xaero waypoints from the last scan (no server communication - Xaero
 *               waypoints are just local files on your own computer)
 * /gtx clear  - forgets all scanned homes for this server (does not touch Xaero waypoints)
 */
public final class GtxCommand {

    private GtxCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("gtx")
                .then(ClientCommandManager.literal("menu").executes(ctx -> {
                    GuitoxaeroClient.openMenu(MinecraftClient.getInstance());
                    return 1;
                }))
                .then(ClientCommandManager.literal("list").executes(ctx -> {
                    listHomes(ctx.getSource());
                    return 1;
                }))
                .then(ClientCommandManager.literal("sync").executes(ctx -> {
                    syncWaypoints(ctx.getSource());
                    return 1;
                }))
                .then(ClientCommandManager.literal("clear").executes(ctx -> {
                    String key = HomeStorage.currentServerKey(MinecraftClient.getInstance());
                    HomeStorage.get().clear(key);
                    ctx.getSource().sendFeedback(Text.literal("[GuiToXaero] Cleared scanned homes for this server."));
                    return 1;
                }))
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(Text.literal(
                            "[GuiToXaero] Open the homes GUI and press your scan key to record, then use "
                                    + "/gtx sync, /gtx menu, /gtx list or /gtx clear."));
                    return 1;
                })
        );
    }

    private static void syncWaypoints(FabricClientCommandSource source) {
        String key = HomeStorage.currentServerKey(MinecraftClient.getInstance());
        List<HomeEntry> homes = HomeStorage.get().getHomes(key);
        if (homes.isEmpty()) {
            source.sendFeedback(Text.literal("[GuiToXaero] No scanned homes to sync yet. Record a scan first."));
            return;
        }
        String result = XaeroWaypointBridge.syncHomes(homes);
        source.sendFeedback(Text.literal("[GuiToXaero] Waypoints: " + result
                + " (set \"" + XaeroWaypointBridge.WAYPOINT_SET_NAME + "\"). This only wrote local Xaero data, "
                + "nothing was sent to the server."));
    }

    private static void listHomes(FabricClientCommandSource source) {
        String key = HomeStorage.currentServerKey(MinecraftClient.getInstance());
        List<HomeEntry> homes = HomeStorage.get().getHomes(key);
        if (homes.isEmpty()) {
            source.sendFeedback(Text.literal("[GuiToXaero] No homes scanned yet for this server."));
            return;
        }
        source.sendFeedback(Text.literal("[GuiToXaero] " + homes.size() + " homes:"));
        for (HomeEntry home : homes) {
            source.sendFeedback(Text.literal(" - " + home.name() + "  (" + home.coordsString() + ", " + home.dimensionId() + ")"));
        }
    }
}
