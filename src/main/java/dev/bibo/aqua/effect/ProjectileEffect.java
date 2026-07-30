package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.fx.ImpactFx;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A travelling jet of water. Supports straight flight (bullet/shotgun/needle) and a ballistic,
 * homing arc (dragon). Swept ray-casts plus a generous hit radius make it connect reliably.
 *
 * <p><b>Shape.</b> The water is laid out along the head's own recent <i>path</i>, not as a rigid stick
 * bolted to the current velocity vector. Two reasons. First, a rigid formation has to guess where its
 * tip goes and the old one guessed wrong — it put three quarters of its length <i>in front of</i> the
 * point that actually collides, so the visible spearhead was metres ahead of the hit and the tail
 * stuck out of the caster's face; from the side it read as a log sliding past rather than a shot.
 * Second, on an arcing shot a rigid stick stays straight while the trajectory bends, which is exactly
 * the "flying sideways" look. Following the path makes the water curve with the arc for free.
 *
 * <p>The profile along that path is deliberately arrow-shaped: a needle-thin nose, a tight shaft, and
 * a plume that flares out behind — with each drop stretched hard along the local direction of travel
 * at the nose and progressively rounder toward the tail.
 */
public final class ProjectileEffect extends BaseEffect {

    private static final double GOLDEN = Math.PI * (3 - Math.sqrt(5));
    /** How many cross-sections the jet is drawn as. Sampling the path is the per-tick cost, so this
     *  is sampled once per ring and shared by every drop assigned to it, not recomputed per drop. */
    private static final int RINGS = 14;

    private final double dmg;
    private final double aoe;
    private final boolean pierce;
    private final int life;
    private final int breakDepth;
    private final double power;
    private final double gravity;
    private final LivingEntity homing;
    private final double homingStrength;
    private final double raySize;
    private final double trailLength;
    private final double girth;
    private final Set<UUID> hitSet = new HashSet<>();

    // Per-drop placement within the jet, fixed at spawn.
    private final int[] ringOf;
    private final double[] thetaOf;
    private final double[] radiusOf;   // 0..1 fraction of the ring's radius

    // Per-ring sampling of the path, recomputed once per tick.
    private final Location[] ringPos = new Location[RINGS];
    private final Vector[] ringDir = new Vector[RINGS];

    /** Head positions, newest first, trimmed to {@link #trailLength} of arc. */
    private final List<Location> path = new ArrayList<>();
    private double roll = 0;
    private double travelled = 0;

    private Vector dir;
    private Vector vel;
    private Location pos;
    private boolean done = false;
    private ImpactFx signature = ImpactFx.SPEAR;

    public ProjectileEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                            Location start, Vector dir, double speed, double spearLength, double spearRadius,
                            double dmg, double aoe, boolean pierce, int life, int breakDepth, double power,
                            double gravity, LivingEntity homing, double homingStrength, double hitRadius) {
        super(plugin, player, blocks);
        this.pos = start.clone();
        this.dir = dir.clone().normalize();
        this.vel = this.dir.clone().multiply(speed);
        this.dmg = dmg;
        this.aoe = aoe;
        this.pierce = pierce;
        this.life = life;
        this.breakDepth = breakDepth;
        this.power = power;
        this.gravity = gravity;
        this.homing = homing;
        this.homingStrength = homingStrength;
        this.raySize = hitRadius > 0 ? hitRadius : Math.max(1.8, spearRadius * 2.4);
        this.trailLength = Math.max(1.2, spearLength);
        this.girth = Math.max(0.15, spearRadius);

        int n = Math.max(1, blocks.size());
        this.ringOf = new int[blocks.size()];
        this.thetaOf = new double[blocks.size()];
        this.radiusOf = new double[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            double t = n <= 1 ? 0 : i / (double) (n - 1);
            ringOf[i] = (int) Math.round(t * (RINGS - 1));
            thetaOf[i] = GOLDEN * i;
            // sqrt keeps drops spread evenly over the disc instead of bunching at the axis
            radiusOf[i] = Math.sqrt(((i * 37) % 100) / 100.0);
        }
        path.add(pos.clone());
        WorldFx.sound(plugin, start, "minecraft:entity.breeze.shoot", 1.0f, 0.8f);
    }

    /** Which impact signature this bolt leaves — a needle should not land like a dragon. */
    public ProjectileEffect signature(ImpactFx fx) {
        this.signature = fx;
        return this;
    }

    @Override
    public boolean tick() {
        if (done) return true;
        age++;
        World w = pos.getWorld();
        if (w == null) return true;
        // A projectile outlives its caster's presence, but not their world: once they are elsewhere
        // nothing here can be attributed to them sensibly.
        if (!w.equals(homeWorld)) return true;

        Vector moveDir = vel.lengthSquared() > 1e-6 ? vel.clone().normalize() : dir;
        double step = Math.max(0.1, vel.length());

        Location next = pos.clone().add(moveDir.clone().multiply(step));
        RayTraceResult br = w.rayTraceBlocks(pos, moveDir, step + 0.3, FluidCollisionMode.NEVER, true);

        if (pierce) {
            for (LivingEntity le : nearbyLiving(next, raySize, hitSet)) {
                WorldFx.hurt(plugin, le, dmg, player);
                le.setVelocity(le.getVelocity().add(moveDir.clone().multiply(0.4).setY(0.15)));
                hitSet.add(le.getUniqueId());
                WorldFx.splash(plugin, le.getLocation().add(0, 1, 0), 10);
            }
            if (br != null) {
                impactBlock(br);
                return true;
            }
        } else {
            // Reliable radius hit (the swept ray was missing fast/spread bolts -> water under the target).
            List<LivingEntity> near = nearbyLiving(next, raySize, hitSet);
            if (!near.isEmpty()) {
                LivingEntity le = near.get(0);
                double best = le.getLocation().distanceSquared(next);
                for (LivingEntity o : near) {
                    double d = o.getLocation().distanceSquared(next);
                    if (d < best) {
                        best = d;
                        le = o;
                    }
                }
                hitEntity(le);
                return true;
            }
            if (br != null) {
                impactBlock(br);
                return true;
            }
        }

        pos = next;

        if (gravity > 0) vel.setY(vel.getY() - gravity);
        if (homing != null && homingStrength > 0 && !homing.isDead() && homing.isValid()) {
            Vector targetC = homing.getLocation().add(0, homing.getHeight() * 0.5, 0).toVector();
            if (gravity > 0) {
                // steer only the horizontal component so the ballistic arc is preserved
                Vector toT = targetC.clone().subtract(pos.toVector());
                Vector horiz = new Vector(vel.getX(), 0, vel.getZ());
                double hs = horiz.length();
                Vector desired = new Vector(toT.getX(), 0, toT.getZ());
                if (desired.lengthSquared() > 1e-6) {
                    desired.normalize().multiply(Math.max(hs, 0.5));
                    horiz.multiply(1 - homingStrength).add(desired.multiply(homingStrength));
                    vel.setX(horiz.getX());
                    vel.setZ(horiz.getZ());
                }
            } else {
                // gently curve the whole velocity toward the target (light, escapable)
                Vector toT = targetC.clone().subtract(pos.toVector());
                if (toT.lengthSquared() > 1e-6) {
                    double sp = vel.length();
                    vel.multiply(1 - homingStrength).add(toT.normalize().multiply(sp * homingStrength));
                    if (vel.lengthSquared() > 1e-6) vel.normalize().multiply(sp);
                }
            }
        }
        if (vel.lengthSquared() > 1e-6) dir = vel.clone().normalize();

        pushPath(pos);
        renderJet();
        WorldFx.trail(plugin, pos);

        if (age > life) {
            impactAir(pos);
            return true;
        }
        return false;
    }

    // ---- shape --------------------------------------------------------------

    /** Record the head position and forget whatever has fallen off the back of the trail. */
    private void pushPath(Location p) {
        if (!path.isEmpty() && path.get(0).getWorld() == p.getWorld()) {
            travelled += path.get(0).distance(p);
        }
        path.add(0, p.clone());
        double acc = 0;
        for (int i = 1; i < path.size(); i++) {
            acc += path.get(i - 1).distance(path.get(i));
            if (acc >= trailLength) {
                while (path.size() > i + 1) path.remove(path.size() - 1);
                return;
            }
        }
        if (path.size() > 64) path.remove(path.size() - 1);   // safety valve
    }

    /**
     * How long the jet is right now.
     *
     * <p>Clamped to how far the head has actually flown, because otherwise every ring past the first
     * samples the same (only) path node for the first few ticks and the whole jet sits bunched at the
     * muzzle before unfurling. Growing the trail with the shot makes it read as water being thrown.
     */
    private double liveTrail() {
        return Math.max(0.8, Math.min(trailLength, travelled));
    }

    /** The point {@code s} blocks back along the flown path, and which way the jet is heading there. */
    private void sampleRing(int ring) {
        double s = liveTrail() * (ring / (double) (RINGS - 1));
        Location here = walkBack(s);
        Location behind = walkBack(Math.min(liveTrail(), s + 0.6));
        Vector d = here.toVector().subtract(behind.toVector());
        ringPos[ring] = here;
        ringDir[ring] = d.lengthSquared() > 1e-6 ? d.normalize() : dir.clone();
    }

    private Location walkBack(double s) {
        if (path.size() < 2) return path.get(0).clone();
        double acc = 0;
        for (int i = 1; i < path.size(); i++) {
            double d = path.get(i - 1).distance(path.get(i));
            if (acc + d >= s) {
                double f = d < 1e-6 ? 0 : (s - acc) / d;
                return Geometry.lerp(path.get(i - 1), path.get(i), f);
            }
            acc += d;
        }
        return path.get(path.size() - 1).clone();
    }

    /**
     * Arrow profile as a fraction of {@link #girth}: a point at the nose, a tight shaft, then a
     * plume flaring out behind. This is what makes it read as a shot rather than a lump of water.
     */
    private static double profile(double t) {
        if (t < 0.10) return 0.28 * (t / 0.10);              // the point itself
        if (t < 0.45) return 0.28;                            // shaft
        return 0.28 + 0.72 * ((t - 0.45) / 0.55);             // flaring plume
    }

    private void renderJet() {
        roll += 0.22;
        for (int r = 0; r < RINGS; r++) sampleRing(r);

        for (int i = 0; i < blocks.size(); i++) {
            int r = ringOf[i];
            double t = r / (double) (RINGS - 1);
            Location c = ringPos[r];
            Vector f = ringDir[r];

            Vector ref = Math.abs(f.getY()) < 0.95 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
            Vector uu = f.clone().crossProduct(ref).normalize();
            Vector vv = f.clone().crossProduct(uu).normalize();

            double rad = girth * profile(t) * radiusOf[i];
            double th = thetaOf[i] + roll * (0.3 + t);   // the plume swirls, the nose barely does
            Location p = c.clone()
                    .add(uu.clone().multiply(Math.cos(th) * rad))
                    .add(vv.clone().multiply(Math.sin(th) * rad));

            // Needle at the nose, rounding out toward the tail.
            blocks.get(i).stretch(f, (float) (3.8 - 2.4 * t), (float) (0.32 + 0.68 * t));
            place(i, p);
        }
    }

    // ---- impacts ------------------------------------------------------------

    private void hitEntity(LivingEntity le) {
        done = true;
        Location at = le.getLocation().add(0, le.getHeight() * 0.5, 0);
        WorldFx.hurt(plugin, le, dmg, player); // guaranteed damage on the struck target
        le.setVelocity(le.getVelocity().add(dir.clone().multiply(0.3).setY(0.2)));
        // Neighbours only — the struck target already took its full hit above.
        WorldFx.damage(plugin, at, Math.max(1.5, aoe), dmg * 0.5, player, 0.3, 0.5, le);
        WorldFx.impact(plugin, at, aoe * 0.8, breakDepth, power);
        signature.play(plugin, at, power);
    }

    private void impactBlock(RayTraceResult br) {
        done = true;
        // The ray was cast in the projectile's world, not the caster's. Using the caster's meant that
        // if they changed world mid-flight — portal, /tp, another plugin — the crater, the water and
        // the area damage all landed at those coordinates in the destination world instead.
        Location at = br.getHitPosition().toLocation(pos.getWorld());
        WorldFx.damage(plugin, at, aoe, dmg, player, 0.25, 0.45);
        WorldFx.impact(plugin, at, br.getHitBlockFace(), br.getHitBlock(), aoe, breakDepth, power);
        signature.play(plugin, at, power);
    }

    private void impactAir(Location at) {
        done = true;
        WorldFx.damage(plugin, at, aoe, dmg * 0.8, player, 0.2, 0.35);
        WorldFx.impact(plugin, at, aoe * 0.7, breakDepth, power);
        signature.play(plugin, at, power * 0.7);
    }
}
