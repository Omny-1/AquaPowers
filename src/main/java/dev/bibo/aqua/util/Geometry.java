package dev.bibo.aqua.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Point-cloud shape generators and small math helpers used to arrange water blocks. */
public final class Geometry {

    private Geometry() {}

    private static final double GOLDEN = Math.PI * (3.0 - Math.sqrt(5.0));

    // Every generator here obeys one contract: it returns EXACTLY n offsets, including n == 0.
    // Three of the four used to answer a request for zero with a single point while the fourth
    // answered with none — an inconsistency sitting precisely in the case the self-check never
    // exercised. Nothing reaches it today, but "one offset per drop" is what every caller assumes
    // when it iterates blocks and indexes offsets in lockstep.

    /** Evenly distributed points on a sphere surface (Fibonacci sphere). */
    public static List<Vector> sphere(int n, double r) {
        if (n <= 0) return new ArrayList<>();
        List<Vector> out = new ArrayList<>(n);
        if (n == 1) {
            out.add(new Vector(0, r, 0));
            return out;
        }
        for (int i = 0; i < n; i++) {
            double y = 1.0 - (i / (double) (n - 1)) * 2.0;
            double rad = Math.sqrt(Math.max(0, 1 - y * y));
            double theta = GOLDEN * i;
            out.add(new Vector(Math.cos(theta) * rad, y, Math.sin(theta) * rad).multiply(r));
        }
        return out;
    }

    /** Points filling a solid ball, denser toward the surface. */
    public static List<Vector> ball(int n, double r) {
        if (n <= 0) return new ArrayList<>();
        List<Vector> out = new ArrayList<>(n);
        if (n == 1) {
            out.add(new Vector(0, 0, 0));
            return out;
        }
        for (int i = 0; i < n; i++) {
            double y = 1.0 - (i / (double) (n - 1)) * 2.0;
            double rad = Math.sqrt(Math.max(0, 1 - y * y));
            double theta = GOLDEN * i;
            Vector dir = new Vector(Math.cos(theta) * rad, y, Math.sin(theta) * rad);
            double rr = r * Math.cbrt((i + 0.5) / n);
            out.add(dir.multiply(rr));
        }
        return out;
    }

    /**
     * Awakening silhouette: a dense core wrapped in two counter-tilted rings, like a gyroscope.
     * Deliberately reads as "not a ball" at a glance so awakening is visible across the map.
     */
    public static List<Vector> gyroscope(int n, double r) {
        if (n <= 0) return new ArrayList<>();
        List<Vector> out = new ArrayList<>(n);
        if (n == 1) {
            out.add(new Vector(0, 0, 0));
            return out;
        }
        int core = Math.max(1, n / 5);
        int perRing = Math.max(1, (n - core) / 2);
        for (Vector v : ball(core, r * 0.42)) out.add(v);
        // Ring A tilts forward, ring B tilts the other way — they visibly cross.
        addRing(out, perRing, r, Math.toRadians(22), 0);
        addRing(out, n - out.size(), r * 0.86, Math.toRadians(-58), Math.PI / 3);
        return out;
    }

    private static void addRing(List<Vector> out, int n, double r, double tilt, double phase) {
        double ct = Math.cos(tilt), st = Math.sin(tilt);
        for (int i = 0; i < n; i++) {
            double a = phase + (2 * Math.PI * i) / Math.max(1, n);
            double x = Math.cos(a) * r, z = Math.sin(a) * r;
            // rotate the flat ring about the X axis by `tilt`
            out.add(new Vector(x, -z * st, z * ct));
        }
    }

    /** Points on the surface of a vertical cylinder — fountains, columns, pillars of water. */
    public static List<Vector> column(int n, double r, double height) {
        if (n <= 0) return new ArrayList<>();
        List<Vector> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double f = n <= 1 ? 0.5 : i / (double) (n - 1);
            double a = GOLDEN * i;
            double rr = r * (0.35 + 0.65 * Math.sin(f * Math.PI));
            out.add(new Vector(Math.cos(a) * rr, f * height, Math.sin(a) * rr));
        }
        return out;
    }

    /** Visitor for {@link #forEachInSphere}: return false to stop the walk early. */
    public interface OffsetVisitor {
        boolean visit(int dx, int dy, int dz);
    }

    /**
     * Visit every integer offset inside a sphere of radius R, ordered by expanding cubic shells so
     * nearest offsets come first and a caller that has found enough can stop immediately. Each offset
     * is visited exactly once.
     */
    public static void forEachInSphere(int R, OffsetVisitor v) {
        int r2 = R * R;
        for (int r = 0; r <= R; r++) {
            for (int dy = -r; dy <= r; dy++) {
                boolean cap = Math.abs(dy) == r;             // top/bottom face: the whole square is new
                for (int dx = -r; dx <= r; dx++) {
                    // On a side face only the dz = ±r rim is new; the interior belongs to a smaller shell.
                    int stepZ = (cap || Math.abs(dx) == r) ? 1 : Math.max(1, 2 * r);
                    for (int dz = -r; dz <= r; dz += stepZ) {
                        if (dx * dx + dy * dy + dz * dz > r2) continue;
                        if (!v.visit(dx, dy, dz)) return;
                    }
                }
            }
        }
    }

    /** Deal a list round-robin into {@code parts} buckets (splitting an orb between clones/mines/pellets). */
    public static <T> List<List<T>> partition(List<T> src, int parts) {
        if (parts < 1) parts = 1;
        List<List<T>> out = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) out.add(new ArrayList<>());
        for (int i = 0; i < src.size(); i++) out.get(i % parts).add(src.get(i));
        return out;
    }

    public static Vector rotateY(Vector v, double ang) {
        double c = Math.cos(ang), s = Math.sin(ang);
        return new Vector(v.getX() * c - v.getZ() * s, v.getY(), v.getX() * s + v.getZ() * c);
    }

    public static double easeOut(double t) {
        t = clamp01(t);
        double inv = 1 - t;
        return 1 - inv * inv * inv;
    }

    public static double easeIn(double t) {
        t = clamp01(t);
        return t * t * t;
    }

    public static double clamp01(double t) {
        return t < 0 ? 0 : (t > 1 ? 1 : t);
    }

    public static Location lerp(Location a, Location b, double t) {
        return new Location(a.getWorld(),
                a.getX() + (b.getX() - a.getX()) * t,
                a.getY() + (b.getY() - a.getY()) * t,
                a.getZ() + (b.getZ() - a.getZ()) * t);
    }
}
