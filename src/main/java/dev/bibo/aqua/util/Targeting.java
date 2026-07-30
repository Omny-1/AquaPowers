package dev.bibo.aqua.util;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Ray-casting helpers: where is the player aiming, what are they aiming at. */
public final class Targeting {

    private Targeting() {}

    /** The point the player is looking at: first solid block hit, else max range. */
    public static Location lookPoint(Player p, double maxDist) {
        Location eye = p.getEyeLocation();
        World w = eye.getWorld();
        Vector dir = eye.getDirection();
        RayTraceResult r = w.rayTraceBlocks(eye, dir, maxDist, FluidCollisionMode.NEVER, true);
        if (r != null && r.getHitPosition() != null) {
            return r.getHitPosition().toLocation(w);
        }
        return eye.clone().add(dir.multiply(maxDist));
    }

    public static LivingEntity targetEntity(Player p, double maxDist) {
        Location eye = p.getEyeLocation();
        World w = eye.getWorld();
        RayTraceResult r = w.rayTraceEntities(eye, eye.getDirection(), maxDist, 0.6,
                e -> e != null && e != p && e instanceof LivingEntity && !e.isDead());
        if (r != null && r.getHitEntity() instanceof LivingEntity le) return le;
        return null;
    }

    /**
     * Like {@link #groundBelow}, but water counts as a surface you can ride, and it reports failure
     * instead of inventing an answer. {@code groundBelow} falls back to the probe position when it
     * finds nothing, which is fine for an impact but catastrophic for anything that feeds its own
     * output back in as the next probe — over an ocean the caller would climb into the sky.
     *
     * @return the spot on top of the first solid-or-water block, or {@code null} if there is none.
     */
    public static Location surfaceBelow(Location from, int maxDown) {
        World w = from.getWorld();
        int x = from.getBlockX(), z = from.getBlockZ();
        int startY = from.getBlockY();
        for (int i = 0; i <= maxDown; i++) {
            Block b = w.getBlockAt(x, startY - i, z);
            if (b.getType().isSolid() || b.getType() == org.bukkit.Material.WATER) {
                return new Location(w, from.getX(), b.getY() + 1.0, from.getZ());
            }
        }
        return null;
    }

    /** Walk down from the location to the first solid block; returns the spot on top of it. */
    public static Location groundBelow(Location from, int maxDown) {
        World w = from.getWorld();
        int x = from.getBlockX(), z = from.getBlockZ();
        int startY = from.getBlockY();
        for (int i = 0; i <= maxDown; i++) {
            Block b = w.getBlockAt(x, startY - i, z);
            if (b.getType().isSolid()) {
                return new Location(w, x + 0.5, b.getY() + 1.0, z + 0.5);
            }
        }
        return from.clone();
    }
}
