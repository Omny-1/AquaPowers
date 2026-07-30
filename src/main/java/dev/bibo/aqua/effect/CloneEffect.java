package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/** Splits the orb into several humanoid water clones that hunt nearby enemies. */
public final class CloneEffect extends BaseEffect {

    private final int count;
    private final int duration;
    private final double dmg;
    private final double speed;

    private final List<List<WaterBlock>> groups;
    private final List<List<Vector>> shapes;
    private final Location[] pos;
    private final int[] lastHit;

    public CloneEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                       int count, int duration, double dmg, double speed) {
        super(plugin, player, blocks);
        this.count = Math.max(1, count);
        this.duration = duration;
        this.dmg = dmg;
        this.speed = speed;
        this.groups = Geometry.partition(blocks, this.count);
        this.shapes = new java.util.ArrayList<>();
        this.pos = new Location[this.count];
        this.lastHit = new int[this.count];

        Location base = player.getLocation();
        for (int k = 0; k < this.count; k++) {
            double a = (2 * Math.PI * k) / this.count;
            pos[k] = base.clone().add(Math.cos(a) * 2.0, 0, Math.sin(a) * 2.0);
            List<Vector> ball = Geometry.ball(groups.get(k).size(), 0.45);
            for (Vector v : ball) v.setX(v.getX() * 0.6).setZ(v.getZ() * 0.6).setY(v.getY() * 2.4 + 1.1);
            shapes.add(ball);
        }
        WorldFx.sound(plugin, base, "minecraft:entity.player.splash", 1.0f, 1.3f);
    }

    @Override
    public boolean tick() {
        if (!casterValid()) return true;
        age++;
        for (int k = 0; k < count; k++) {
            LivingEntity tgt = nearest(pos[k]);
            if (tgt != null) {
                Vector d = tgt.getLocation().add(0, 0.4, 0).toVector().subtract(pos[k].toVector());
                if (d.length() > speed) d.normalize().multiply(speed);
                pos[k].add(d);
                if (pos[k].distanceSquared(tgt.getLocation()) < 3.0 && age - lastHit[k] >= 10) {
                    WorldFx.hurtPeriodic(plugin, tgt, dmg, player);
                    tgt.setVelocity(tgt.getVelocity().add(d.clone().multiply(0.25).setY(0.1)));
                    lastHit[k] = age;
                    splash(pos[k].clone().add(0, 1, 0), 12);
                }
            } else {
                // Idle: drift gently around the caster.
                double a = age * 0.05 + (2 * Math.PI * k) / count;
                Location want = player.getLocation().add(Math.cos(a) * 2.2, 0, Math.sin(a) * 2.2);
                pos[k].add(want.toVector().subtract(pos[k].toVector()).multiply(0.15));
            }

            double bob = Math.sin(age * 0.2 + k) * 0.08;
            List<WaterBlock> g = groups.get(k);
            List<Vector> shape = shapes.get(k);
            for (int j = 0; j < g.size(); j++) {
                // Drops elongated on the vertical give the clones a standing, humanoid silhouette
                // instead of a floating clump of cubes.
                g.get(j).stretch(new Vector(0, 1, 0), 2.0f, 0.7f);
                g.get(j).teleport(pos[k].clone().add(shape.get(j).getX(),
                        shape.get(j).getY() + bob, shape.get(j).getZ()));
            }
        }

        if (age > duration) {
            for (Location p : pos) splash(p.clone().add(0, 0.5, 0), 18);
            return true;
        }
        return false;
    }

    private LivingEntity nearest(Location from) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity le : nearbyLiving(from, 18, null)) {
            double d = le.getLocation().distanceSquared(from);
            if (d < bd) {
                bd = d;
                best = le;
            }
        }
        return best;
    }
}
