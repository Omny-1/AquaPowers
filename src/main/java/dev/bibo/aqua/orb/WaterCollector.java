package dev.bibo.aqua.orb;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.Protect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Finds, drains and restores real water blocks around a player. */
public final class WaterCollector {

    private WaterCollector() {}

    /** A real water block that was (or will be) drained, remembering its original state. */
    public record Drained(Location loc, BlockData saved) {}

    /** Hard budget on block lookups per scan, so a big radius over dry land can't stall the tick. */
    private static final int MAX_SCAN = 120_000;

    /** Nearest water around the player, custom radius. */
    public static List<Drained> findSources(Player p, int R, int maxBlocks) {
        return findSources(p, p.getLocation(), R, maxBlocks);
    }

    /**
     * Nearest water sources within a radius of an arbitrary centre (e.g. where the player looks).
     * Walks outward one cubic shell at a time and stops the moment it has enough, so standing in an
     * ocean costs a few hundred lookups instead of scanning (and sorting) the whole sphere.
     *
     * <p><b>Only loaded chunks are examined.</b> {@code World.getBlockAt} goes through
     * {@code getChunkAt}, which loads the chunk — and <i>generates</i> it if it does not exist yet.
     * The ultimate scans radius 60, i.e. up to ~64 chunk columns, and its worst case for lookups
     * (dry land, no early exit) is exactly its worst case for generation: one cast on fresh terrain
     * could drive dozens of chunks of synchronous worldgen and trip the watchdog. Skipping unloaded
     * chunks caps the whole class of problem, at the cost of not finding water nobody is near.
     */
    public static List<Drained> findSources(Player p, Location center, int R, int maxBlocks) {
        World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        int minY = w.getMinHeight(), maxY = w.getMaxHeight();
        List<Drained> out = new ArrayList<>(Math.min(maxBlocks, 256));
        int[] scanned = {0};

        Geometry.forEachInSphere(R, (dx, dy, dz) -> {
            if (++scanned[0] > MAX_SCAN) return false;
            int y = cy + dy;
            if (y < minY || y >= maxY) return true;
            int x = cx + dx, z = cz + dz;
            if (!w.isChunkLoaded(x >> 4, z >> 4)) return true;
            Block b = w.getBlockAt(x, y, z);
            if (isCollectable(b)) {
                out.add(new Drained(b.getLocation(), b.getBlockData().clone()));
                return out.size() < maxBlocks;
            }
            return true;
        });
        return out;
    }

    /** Cheap gate check: is there at least one water source within R blocks of the player? */
    public static boolean anySourceNear(Player p, int R) {
        return !findSources(p, p.getLocation(), R, 1).isEmpty();
    }

    /**
     * May we take this block's water?
     *
     * <p>Two exclusions, both because "remove some water" is not the harmless operation it sounds
     * like. <b>Waterlogged blocks</b> are structure, not a puddle — draining stairs, slabs or a
     * conduit frame silently breaks whatever was built there, and the owner has no way to connect
     * the failure to a water ability. <b>Water holding a plant</b> (kelp, seagrass, coral) destroys
     * the plant on the block update, and restoring the water later does not bring it back — one
     * collect over a reef killed the reef permanently.
     */
    public static boolean isCollectable(Block b) {
        if (b.getType() != Material.WATER) return false;              // structure is off-limits
        if (!(b.getBlockData() instanceof Levelled lv) || lv.getLevel() != 0) return false;
        return !holdsLife(b) && !holdsLife(b.getRelative(0, 1, 0));
    }

    private static boolean holdsLife(Block b) {
        String n = b.getType().name();
        return n.contains("KELP") || n.contains("SEAGRASS") || n.contains("CORAL")
                || n.contains("SEA_PICKLE") || n.equals("BUBBLE_COLUMN");
    }

    /**
     * Remove the real water. Uses physics updates so the surrounding, un-collected water
     * keeps flowing and partially refills the emptied spots (oceans/lakes self-heal, streams flow on).
     *
     * <p>Runs the batch past {@link Protect} first: taking 160 blocks out of someone's aquarium is a
     * terrain edit like any other, and it used to be the one edit path that skipped the gate — which
     * also meant logging plugins never recorded it.
     */
    public static void drain(AquaWaterPlugin plugin, List<Drained> list) {
        if (list.isEmpty()) return;
        List<Block> wanted = new ArrayList<>(list.size());
        for (Drained d : list) wanted.add(d.loc().getBlock());
        Location origin = list.get(0).loc();
        List<Block> allowed = Protect.filter(plugin, origin, wanted);
        if (allowed.size() < wanted.size()) {
            // Keep the bookkeeping honest: only blocks we actually took stay in the list, so the
            // orb never promises to give back water it was not allowed to remove.
            java.util.Set<Location> ok = new java.util.HashSet<>();
            for (Block b : allowed) ok.add(b.getLocation());
            list.removeIf(d -> !ok.contains(d.loc()));
        }
        for (Drained d : list) {
            try {
                d.loc().getBlock().setType(Material.AIR, true);
            } catch (Exception ignored) {
            }
        }
    }

    /** Put the original water back — but only where our own hole is still a hole. */
    public static void restore(List<Drained> list) {
        for (Drained d : list) {
            try {
                Block b = d.loc().getBlock();
                if (!b.isEmpty() && b.getType() != Material.WATER) continue; // someone built here
                b.setBlockData(d.saved(), false);
            } catch (Exception ignored) {
            }
        }
    }
}
