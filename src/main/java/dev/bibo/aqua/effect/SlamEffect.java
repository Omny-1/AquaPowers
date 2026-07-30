package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.fx.ImpactFx;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/** A mass of water that forms above a point and slams down (fist/meteor) or erupts up (geyser). */
public final class SlamEffect extends BaseEffect {

    private final Location target;     // ground-level air block to strike
    private final Location apex;       // where the mass forms before striking
    private final double aoe;
    private final double dmg;
    private final double knockUp;
    private final int breakDepth;
    private final boolean geyser;
    private final double colHeight;
    private final double power;

    private final List<Location> start;
    private final List<Vector> shape;     // blob shape for fist/meteor
    private final List<double[]> column;  // (ringX, yFrac, ringZ) for geyser

    private final int windup;
    private final int strike = 7;
    private final int dwell = 8;
    private boolean impacted = false;

    public SlamEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                      Location target, double rise, double aoe, double dmg, double knockUp,
                      int breakDepth, boolean geyser, int windup, double colHeight, double power) {
        super(plugin, player, blocks);
        this.target = target.clone();
        this.aoe = aoe;
        this.dmg = dmg;
        this.knockUp = knockUp;
        this.breakDepth = breakDepth;
        this.geyser = geyser;
        this.windup = Math.max(4, windup);
        this.colHeight = colHeight;
        this.power = power;
        this.apex = target.clone().add(0, rise, 0);
        this.start = snapshot();
        this.shape = Geometry.ball(blocks.size(), Math.max(0.8, aoe * 0.45));

        this.column = new java.util.ArrayList<>(blocks.size());
        int n = Math.max(1, blocks.size());
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < blocks.size(); i++) {
            double a = golden * i;
            double rr = 0.35 + 0.25 * ((i % 7) / 7.0);
            double yf = n == 1 ? 0.5 : (i / (double) (n - 1));
            column.add(new double[]{Math.cos(a) * rr, yf, Math.sin(a) * rr});
        }
    }

    @Override
    public boolean tick() {
        age++;
        if (age <= windup) {
            double t = Geometry.easeOut(age / (double) windup);
            for (int i = 0; i < blocks.size(); i++) {
                Location to = (geyser ? target : apex).clone().add(shape.get(i));
                place(i, Geometry.lerp(start.get(i), to, t));
            }
            if (age == 1) WorldFx.sound(plugin, target, "minecraft:item.bucket.fill", 1.0f, 0.6f);
            return false;
        }

        int st = age - windup;
        if (st <= strike) {
            double t = Geometry.easeIn(st / (double) strike);
            if (!geyser) {
                Location base = Geometry.lerp(apex, target, t);
                // A falling mass elongates as it accelerates — the meteor is a teardrop, not a ball.
                stretchAll(new Vector(0, -1, 0), (float) (1.0 + 1.8 * t), (float) (1.05 - 0.3 * t));
                for (int i = 0; i < blocks.size(); i++) place(i, base.clone().add(shape.get(i)));
            } else {
                stretchAll(new Vector(0, 1, 0), 2.6f, 0.6f);   // a jet, not a stack of cubes
                for (int i = 0; i < blocks.size(); i++) {
                    double[] c = column.get(i);
                    place(i, target.clone().add(c[0], c[1] * colHeight * t, c[2]));
                }
            }
            return false;
        }

        if (!impacted) {
            impact();
            impacted = true;
            return false;
        }

        // Dwell: water bursts outward then despawns.
        double t = (st - strike) / (double) dwell;
        for (int i = 0; i < blocks.size(); i++) {
            Vector out = shape.get(i).clone().multiply(1 + t * 2.2);
            Location base = geyser ? target.clone().add(0, colHeight * 0.6, 0) : target.clone();
            // Every drop flies outward along its own escape vector, thinning as it goes.
            if (out.lengthSquared() > 1e-6) {
                blocks.get(i).stretch(out, (float) (1.4 + 1.6 * t), (float) Math.max(0.3, 1.0 - 0.6 * t));
            }
            place(i, base.add(out));
        }
        return st - strike > dwell;
    }

    private void impact() {
        if (geyser) {
            WorldFx.damage(plugin, target, Math.max(2.2, aoe), dmg, player, knockUp, 0.15);
            if (breakDepth > 0) WorldFx.impact(plugin, target, Math.max(1.5, aoe * 0.6), breakDepth, power);
            // Ride your own geyser: if you're standing over it, get launched up — no self-damage.
            if (casterValid()
                    && player.getWorld().equals(target.getWorld())
                    && player.getLocation().distance(target) <= Math.max(2.5, aoe) + 1.0) {
                player.setVelocity(player.getVelocity().setY(Math.max(knockUp, 1.0)));
                player.setFallDistance(0);
            }
            ImpactFx.GEYSER.play(plugin, target, power);
        } else {
            WorldFx.damage(plugin, target, aoe, dmg, player, knockUp, 0.55);
            if (breakDepth > 0) WorldFx.impact(plugin, target, aoe, breakDepth, power);
            ImpactFx.METEOR.play(plugin, target, power);
        }
    }
}
