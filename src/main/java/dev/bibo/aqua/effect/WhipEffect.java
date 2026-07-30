package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.fx.ImpactFx;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A long lash of water that sweeps an arc in front of the caster, with a wide hit radius. */
public final class WhipEffect extends BaseEffect {

    private final double length;
    private final double dmg;
    private final double knock;
    private final int sweepTicks;
    private final double arc;
    private final boolean vertical;
    private final double hitRadius;

    private final Vector baseDir;
    private final Vector horizPerp;
    private final Set<java.util.UUID> hitSet = new HashSet<>();

    public WhipEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                      double length, double dmg, double knock, int sweepTicks, double arc, boolean vertical) {
        super(plugin, player, blocks);
        this.length = length;
        this.dmg = dmg;
        this.knock = knock;
        this.sweepTicks = Math.max(4, sweepTicks);
        this.arc = arc;
        this.vertical = vertical;
        this.hitRadius = Math.max(2.0, length * 0.25); // doubled, forgiving hit volume
        Vector d = player.getEyeLocation().getDirection();
        d.setY(0);
        if (d.lengthSquared() < 1e-6) d = new Vector(1, 0, 0);
        this.baseDir = d.normalize();
        this.horizPerp = new Vector(-baseDir.getZ(), 0, baseDir.getX()).normalize();
        WorldFx.sound(plugin, player.getLocation(), "minecraft:entity.player.attack.sweep", 1.0f, 0.7f);
    }

    @Override
    public boolean tick() {
        if (!casterValid()) return true;
        age++;
        double t = age / (double) sweepTicks;
        double angle = -arc / 2 + arc * t;

        Vector dir = vertical
                ? baseDir.clone().rotateAroundAxis(horizPerp, angle)
                : Geometry.rotateY(baseDir, angle);
        Vector perp = vertical ? horizPerp : new Vector(-dir.getZ(), 0, dir.getX()).normalize();

        Location hand = player.getEyeLocation().add(0, -0.2, 0);
        Location tip = hand.clone().add(dir.clone().multiply(length));

        int n = blocks.size();
        for (int i = 0; i < n; i++) {
            double f = n <= 1 ? 1 : i / (double) (n - 1);
            double lag = Math.sin(f * Math.PI) * Math.sin(age * 0.6) * 0.7 * f;
            Location pos = hand.clone()
                    .add(dir.clone().multiply(length * f))
                    .add(perp.clone().multiply(lag));
            // The lash is a ribbon: drawn out along its own length, thinning toward the tip.
            blocks.get(i).stretch(dir, (float) (2.6 + 1.6 * f), (float) (0.85 - 0.4 * f));
            place(i, pos);
        }
        WorldFx.trail(plugin, tip);

        // Hit everything within hitRadius of the whip line segment (hand -> tip).
        for (LivingEntity le : nearbyLiving(hand, length + hitRadius, hitSet)) {
            Location c = le.getLocation().add(0, le.getHeight() * 0.5, 0);
            if (distanceToSegment(c.toVector(), hand.toVector(), tip.toVector()) <= hitRadius) {
                WorldFx.hurt(plugin, le, dmg, player);
                le.setVelocity(le.getVelocity().add(dir.clone().multiply(knock).setY(0.35)));
                hitSet.add(le.getUniqueId());
                // Ten mobs in the arc used to mean twenty sounds from one point in one tick.
                if (announceHit()) ImpactFx.WHIP.play(plugin, c, 0.6);
            }
        }

        if (age >= sweepTicks) {
            ImpactFx.WHIP.play(plugin, tip, 1.0);
            return true;
        }
        return false;
    }

    private static double distanceToSegment(Vector p, Vector a, Vector b) {
        Vector ab = b.clone().subtract(a);
        double len2 = ab.lengthSquared();
        if (len2 < 1e-6) return p.distance(a);
        double t = p.clone().subtract(a).dot(ab) / len2;
        t = Math.max(0, Math.min(1, t));
        Vector proj = a.clone().add(ab.multiply(t));
        return p.distance(proj);
    }
}
