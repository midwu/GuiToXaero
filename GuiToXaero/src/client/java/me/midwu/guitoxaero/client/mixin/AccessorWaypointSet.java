package me.midwu.guitoxaero.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;

import java.util.List;

/**
 * Exposes {@code WaypointSet}'s backing list so we can iterate existing
 * waypoints (to detect duplicates) without depending on Xaero having a public
 * getter for it. {@code remap = false} because this targets Xaero's own
 * (un-obfuscated) class, not a Minecraft class, so it is NOT affected by
 * Yarn/Mojmap remapping and doesn't need to match Minecraft's mappings.
 *
 * If this ever fails to apply (Xaero renamed the "list" field), the fix is to
 * open WaypointSet.class in IntelliJ's decompiler and update the field name
 * below - it will not silently break anything else.
 */
@Mixin(value = WaypointSet.class, remap = false)
public interface AccessorWaypointSet {
    @Accessor("list")
    List<Waypoint> getList();
}
