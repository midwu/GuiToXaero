package me.midwu.guitoxaero.client.xaero;

import me.midwu.guitoxaero.client.GuitoxaeroClient;
import me.midwu.guitoxaero.client.home.HomeEntry;
import me.midwu.guitoxaero.client.mixin.AccessorWaypointSet;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.path.XaeroPath;

import java.util.List;
import java.util.Locale;

/**
 * Talks directly to Xaero's Minimap / World Map internals to create and
 * update waypoints.
 *
 * <p>None of this is officially documented/public API - Xaero doesn't ship
 * one. The call chain below (BuiltInHudModules.MINIMAP -&gt; MinimapSession
 * -&gt; WorldManager -&gt; MinimapWorld -&gt; WaypointSet) was verified against
 * real, currently-maintained open source addons for Xaero's Minimap/World Map
 * (e.g. the WaypointAPI helper used by the XaeroPlus addon, and the
 * AccessorWaypointSet/Waypoint construction pattern used by the PinPoint
 * addon), rather than guessed. It should hold for the 26.5.0 / 1.46.0
 * versions requested, but if Xaero ever renames something, everything here
 * is isolated to this one file and wrapped in try/catch so a mismatch fails
 * loudly in the log instead of crashing the game.
 */
public final class XaeroWaypointBridge {

    public static final String WAYPOINT_SET_NAME = "Generated Homes";

    private XaeroWaypointBridge() {}

    /**
     * Adds or updates waypoints for the given homes. Each home is routed into
     * its own dimension's "Generated Homes" waypoint set (creating that set
     * if it doesn't exist yet). An existing waypoint with the same name in
     * that set is treated as "the same home": if its position hasn't
     * changed, it's left alone; if it moved, it's updated in place. This
     * works even for dimensions the player isn't currently standing in.
     *
     * @return a short human-readable summary, e.g. "12 added, 2 updated, 1 dimension unavailable"
     */
    public static String syncHomes(List<HomeEntry> homes) {
        int added = 0;
        int updated = 0;
        int unchanged = 0;
        int failedDimensions = 0;

        for (HomeEntry home : homes) {
            RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(home.dimensionId()));
            MinimapWorld minimapWorld;
            try {
                minimapWorld = getMinimapWorld(dimension);
            } catch (Throwable t) {
                GuitoxaeroClient.LOGGER.warn("[GuiToXaero] Failed to resolve Xaero world for dimension {}", home.dimensionId(), t);
                minimapWorld = null;
            }

            if (minimapWorld == null) {
                failedDimensions++;
                continue;
            }

            try {
                WaypointSet set = getOrCreateWaypointSet(minimapWorld, WAYPOINT_SET_NAME);
                if (set == null) {
                    failedDimensions++;
                    continue;
                }

                List<Waypoint> list = ((AccessorWaypointSet) set).getList();
                Waypoint existing = null;
                for (Waypoint wp : list) {
                    if (wp.getName().equalsIgnoreCase(home.name())) {
                        existing = wp;
                        break;
                    }
                }

                if (existing == null) {
                    set.add(newWaypoint(home));
                    added++;
                } else if (existing.getX() == home.x() && existing.getY() == home.y() && existing.getZ() == home.z()) {
                    unchanged++;
                } else {
                    set.remove(existing);
                    set.add(newWaypoint(home));
                    updated++;
                }
            } catch (Throwable t) {
                GuitoxaeroClient.LOGGER.warn("[GuiToXaero] Failed to sync waypoint for home '{}'", home.name(), t);
                failedDimensions++;
            }
        }

        try {
            xaero.map.mods.SupportMods.xaeroMinimap.requestWaypointsRefresh();
        } catch (Throwable ignored) {
            // Optional: best-effort refresh so newly-added waypoints render/save immediately.
        }

        StringBuilder summary = new StringBuilder();
        summary.append(added).append(" added, ").append(updated).append(" updated");
        if (unchanged > 0) summary.append(", ").append(unchanged).append(" already up to date");
        if (failedDimensions > 0) summary.append(", ").append(failedDimensions).append(" skipped (dimension unavailable)");
        return summary.toString();
    }

    private static Waypoint newWaypoint(HomeEntry home) {
        String initials = home.name().substring(0, Math.min(2, home.name().length())).toUpperCase(Locale.ROOT);
        int colorIndex = Math.floorMod(home.name().toLowerCase(Locale.ROOT).hashCode(), WaypointColor.values().length);
        WaypointColor color = WaypointColor.fromIndex(colorIndex);
        return new Waypoint(home.x(), home.y(), home.z(), home.name(), initials, color, WaypointPurpose.NORMAL);
    }

    private static WaypointSet getOrCreateWaypointSet(MinimapWorld minimapWorld, String name) {
        WaypointSet set = minimapWorld.getWaypointSet(name);
        if (set == null) {
            minimapWorld.addWaypointSet(name);
            set = minimapWorld.getWaypointSet(name);
        }
        return set;
    }

    /**
     * Resolves the {@link MinimapWorld} for an arbitrary dimension of the
     * currently-connected server/world, loading it even if the player has
     * never actually visited that dimension yet this session.
     */
    private static MinimapWorld getMinimapWorld(RegistryKey<World> dimension) {
        MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minimapSession == null) return null;

        MinimapWorld currentWorld = minimapSession.getWorldManager().getCurrentWorld();
        if (currentWorld != null && currentWorld.getDimId() == dimension) {
            return currentWorld;
        }

        var rootContainer = minimapSession.getWorldManager().getCurrentRootContainer();
        if (rootContainer == null) return null;
        for (MinimapWorld world : rootContainer.getWorlds()) {
            if (world.getDimId() == dimension) {
                return world;
            }
        }

        // Dimension hasn't been loaded/visited yet this session - resolve its
        // on-disk path the same way Xaero itself would and load/create it.
        String dimensionDirectoryName = minimapSession.getDimensionHelper().getDimensionDirectoryName(dimension);
        String worldNode = minimapSession.getWorldStateUpdater().getPotentialWorldNode(dimension, true);
        XaeroPath containerPath = minimapSession.getWorldState()
                .getAutoRootContainerPath()
                .resolve(dimensionDirectoryName)
                .resolve(worldNode);
        return minimapSession.getWorldManager().getWorld(containerPath);
    }
}
