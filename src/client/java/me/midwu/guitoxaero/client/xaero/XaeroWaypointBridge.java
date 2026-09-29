package me.midwu.guitoxaero.client.xaero;

import me.midwu.guitoxaero.client.GuitoxaeroClient;
import me.midwu.guitoxaero.client.home.HomeEntry;
import me.midwu.guitoxaero.mixin.client.AccessorWaypointSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
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
 * Writes LOCAL Xaero waypoint data only (no network). Only load this class after
 * checking Xaero is present (see GuitoxaeroClient.syncWaypoints).
 *
 * The Xaero call chain was taken from the open-source XaeroPlus and PinPoint addons.
 */
public final class XaeroWaypointBridge {

    public static final String WAYPOINT_SET_NAME = "Generated Homes";

    private XaeroWaypointBridge() {}

    public static String syncHomes(List<HomeEntry> homes) {
        int added = 0, updated = 0, unchanged = 0, skipped = 0;

        for (HomeEntry home : homes) {
            try {
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(home.dimensionId()));
                MinimapWorld world = getMinimapWorld(dim);
                if (world == null) {
                    skipped++;
                    continue;
                }
                WaypointSet set = world.getWaypointSet(WAYPOINT_SET_NAME);
                if (set == null) {
                    world.addWaypointSet(WAYPOINT_SET_NAME);
                    set = world.getWaypointSet(WAYPOINT_SET_NAME);
                }
                if (set == null) {
                    skipped++;
                    continue;
                }

                Waypoint existing = null;
                for (Waypoint wp : ((AccessorWaypointSet) (Object) set).getList()) {
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
                GuitoxaeroClient.LOGGER.warn("[GuiToXaero] failed to sync home '{}'", home.name(), t);
                skipped++;
            }
        }

        try {
            xaero.map.mods.SupportMods.xaeroMinimap.requestWaypointsRefresh();
        } catch (Throwable ignored) {
            // best-effort refresh
        }

        return "Waypoints in set \"" + WAYPOINT_SET_NAME + "\": " + added + " added, " + updated + " updated, "
                + unchanged + " already up to date, " + skipped + " skipped.";
    }

    private static Waypoint newWaypoint(HomeEntry home) {
        String name = home.name();
        String initials = name.substring(0, Math.min(2, name.length())).toUpperCase(Locale.ROOT);
        WaypointColor color = WaypointColor.fromIndex(Math.floorMod(name.toLowerCase(Locale.ROOT).hashCode(), WaypointColor.values().length));
        return new Waypoint(home.x(), home.y(), home.z(), name, initials, color, WaypointPurpose.NORMAL);
    }

    private static MinimapWorld getMinimapWorld(ResourceKey<Level> dim) {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) return null;
        MinimapWorld current = session.getWorldManager().getCurrentWorld();
        if (current == null) return null;
        if (current.getDimId() == dim) return current;

        var root = session.getWorldManager().getCurrentRootContainer();
        for (MinimapWorld world : root.getWorlds()) {
            if (world.getDimId() == dim) return world;
        }

        String dimDir = session.getDimensionHelper().getDimensionDirectoryName(dim);
        String node = session.getWorldStateUpdater().getPotentialWorldNode(dim, true);
        XaeroPath path = session.getWorldState().getAutoRootContainerPath().resolve(dimDir).resolve(node);
        return session.getWorldManager().getWorld(path);
    }
}