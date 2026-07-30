package dev.bibo.aqua.fx;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * How an individual drop of water looks.
 *
 * <p>A body of water rendered as N identical cubes of one material at one size reads as a lattice,
 * not a liquid. Two cheap tricks fix that and they do most of the visual work in this plugin:
 * a <b>weighted palette</b> (deep blue body, lighter mid-tones, a little white foam) and
 * <b>per-drop size jitter</b>, so no two neighbouring drops line up.
 */
public final class WaterStyle {

    /** material : weight — the default reads as deep water with foam highlights. */
    private static final String[] DEFAULT_PALETTE = {
            "BLUE_STAINED_GLASS:6",
            "LIGHT_BLUE_STAINED_GLASS:4",
            "CYAN_STAINED_GLASS:3",
            "WHITE_STAINED_GLASS:1",
    };

    private final BlockData[] weighted;   // pre-expanded so a pick is one array index
    private final double jitterMin;
    private final double jitterMax;

    private WaterStyle(BlockData[] weighted, double jitterMin, double jitterMax) {
        this.weighted = weighted;
        this.jitterMin = jitterMin;
        this.jitterMax = jitterMax;
    }

    public static WaterStyle fromConfig(FileConfiguration c) {
        List<String> raw = c.getStringList("orb.palette");
        if (raw.isEmpty()) raw = List.of(DEFAULT_PALETTE);

        List<BlockData> pool = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.split(":");
            Material m = Material.matchMaterial(parts[0].trim());
            if (m == null || !m.isBlock()) continue;
            int weight = 1;
            if (parts.length > 1) {
                try {
                    weight = Math.max(1, Math.min(32, Integer.parseInt(parts[1].trim())));
                } catch (NumberFormatException ignored) {
                    // keep weight 1
                }
            }
            BlockData d;
            try {
                d = m.createBlockData();
            } catch (Exception e) {
                continue;
            }
            for (int i = 0; i < weight; i++) pool.add(d);
        }
        // A single legacy `orb.material` still works, and an unusable palette can never leave us empty.
        if (pool.isEmpty()) {
            Material legacy = Material.matchMaterial(c.getString("orb.material", "BLUE_STAINED_GLASS"));
            if (legacy == null || !legacy.isBlock()) legacy = Material.BLUE_STAINED_GLASS;
            pool.add(legacy.createBlockData());
        }

        double lo = c.getDouble("orb.size-jitter-min", 0.72);
        double hi = c.getDouble("orb.size-jitter-max", 1.36);
        if (hi < lo) {
            double t = lo;
            lo = hi;
            hi = t;
        }
        return new WaterStyle(pool.toArray(new BlockData[0]), Math.max(0.05, lo), Math.min(4.0, hi));
    }

    public BlockData pickMaterial() {
        return weighted[ThreadLocalRandom.current().nextInt(weighted.length)];
    }

    /** A drop's own resting size — fixed for its whole life so the mass doesn't shimmer. */
    public float pickScale(float base) {
        return (float) (base * (jitterMin + ThreadLocalRandom.current().nextDouble() * (jitterMax - jitterMin)));
    }
}
