package dev.bibo.aqua.util;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a large detonation crater, as a pure function of position.
 *
 * <p>A single radius with a smooth {@code 1 - h/R} falloff produces a bowl that looks machined —
 * turned on a lathe rather than blown out of the ground. A real charge of that size leaves the
 * <b>union of many overlapping sub-detonations</b>: lobes where craters merge, ridges in the gaps
 * between them, an outline that wanders in and out, and the odd stump of untouched ground still
 * standing inside. That is what this builds.
 *
 * <p>Everything derives from a hash of the impact coordinates, so the same spot always yields the
 * same crater and the result never depends on call order or on how the caller iterates.
 *
 * <p>No Bukkit types here on purpose: the interesting property ("this is not radially symmetric") is
 * worth asserting, and asserting it needs no server.
 */
public final class CraterShape {

    private record SubBlast(double x, double z, double r, double depth) {}

    private final List<SubBlast> blasts;
    private final int cx;
    private final int cz;
    private final double roughness;
    private final int reach;
    private final double maxDepth;

    private CraterShape(List<SubBlast> blasts, int cx, int cz, double roughness, int reach, double maxDepth) {
        this.blasts = blasts;
        this.cx = cx;
        this.cz = cz;
        this.roughness = roughness;
        this.reach = reach;
        this.maxDepth = maxDepth;
    }

    /**
     * @param cx,cz      world coordinates of the impact — the seed, so craters are reproducible
     * @param radius     nominal radius
     * @param maxDepth   depth at the deepest point
     * @param blastCount how many sub-detonations to union; 0 gives a single smooth bowl
     * @param roughness  0..1 — how uneven the sub-blasts are, and how often stumps survive
     */
    public static CraterShape of(int cx, int cz, int radius, int maxDepth, int blastCount, double roughness) {
        double R = Math.max(1, radius);
        double rough = Math.max(0, Math.min(1, roughness));
        List<SubBlast> blasts = new ArrayList<>(blastCount + 1);
        // The main charge sits at the point of impact; the rest fan out around it, deliberately
        // reaching past R so the rim breaks up instead of closing into a circle.
        blasts.add(new SubBlast(0, 0, R * 0.62, maxDepth));
        for (int i = 0; i < blastCount; i++) {
            double n1 = noise01(cx + i * 31, cz - i * 17);
            double n2 = noise01(cx - i * 13, cz + i * 41);
            double n3 = noise01(cx + i * 7, cz + i * 5);
            double ang = n1 * Math.PI * 2;
            double dist = R * (0.15 + 0.85 * Math.sqrt(n2));
            double sr = R * (0.20 + 0.35 * n3) * (1.0 - 0.35 * rough * (1 - n1));
            double sd = maxDepth * (0.45 + 0.75 * n3);
            blasts.add(new SubBlast(Math.cos(ang) * dist, Math.sin(ang) * dist, Math.max(2.0, sr), sd));
        }
        return new CraterShape(blasts, cx, cz, rough, (int) Math.ceil(R) + 6, maxDepth);
    }

    /** How far out from the centre any column could possibly be affected. */
    public int reach() {
        return reach;
    }

    /**
     * Depth to carve at this offset from the impact, or {@code 0} for "leave this column alone".
     * Zero is a real answer here: the holes it leaves are the ragged teeth around the rim and the
     * stumps in the middle.
     */
    public double depthAt(int dx, int dz) {
        double depth = 0;
        for (SubBlast b : blasts) {
            double d = Math.hypot(dx - b.x(), dz - b.z());
            if (d >= b.r()) continue;
            double local = 1.0 - d / b.r();
            depth = Math.max(depth, b.depth() * Math.pow(local, 0.65));
        }
        if (depth <= 0) return 0;

        // Fine grain on top of the lobes, then a threshold — this is what turns a drawn curve into
        // torn ground.
        depth *= 0.72 + 0.56 * noise01(cx + dx, cz + dz);
        if (depth < 0.85) return 0;

        // Occasional survivor: a pillar of untouched ground left standing in the blast.
        if (roughness > 0 && noise01(cx + dx + 977, cz + dz - 613) > 1.0 - 0.05 * roughness) return 0;

        // Two multiplicative terms stack — a sub-blast can be 1.2x the nominal depth and the grain
        // another 1.28x on top — so without this the hole went half again deeper than the configured
        // budget and the knob lied. Clamping keeps the lumpiness and honours the number.
        return Math.min(depth, maxDepth);
    }

    /** Deterministic per-column noise in [0, 1]. */
    private static double noise01(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return (h & 0xFFFF) / 65535.0;
    }
}
