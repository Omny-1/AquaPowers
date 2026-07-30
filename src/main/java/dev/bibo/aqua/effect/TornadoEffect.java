package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.fx.ImpactFx;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Targeting;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/** A water tornado that travels forward, sucking in and flinging entities, leaving a wet trail. */
public final class TornadoEffect extends BaseEffect {

    private final Vector dir;
    private final double radius;
    private final double height;
    private final double maxDist;
    private final double speed;
    private final double dmg;
    private final double pull;
    private final int breakDepth;
    private final double power;

    private final Location origin;
    private double travelled = 0;
    private double spin = 0;

    public TornadoEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                         double radius, double height, double maxDist, double speed,
                         double dmg, double pull, int breakDepth, double power) {
        super(plugin, player, blocks);
        Vector d = player.getEyeLocation().getDirection();
        d.setY(0);
        if (d.lengthSquared() < 1e-6) d = new Vector(1, 0, 0);
        this.dir = d.normalize();
        this.radius = radius;
        this.height = height;
        this.maxDist = maxDist;
        this.speed = speed;
        this.dmg = dmg;
        this.pull = pull;
        this.breakDepth = breakDepth;
        this.power = power;
        this.origin = player.getLocation().clone();
        WorldFx.sound(plugin, origin, "minecraft:entity.player.splash.high_speed", 1.2f, 0.5f);
    }

    @Override
    public boolean tick() {
        age++;
        travelled += speed;
        spin += 0.5;

        Location head = origin.clone().add(dir.clone().multiply(travelled));
        Location base = Targeting.groundBelow(head.clone().add(0, 1.5, 0), 6);
        int n = blocks.size();

        boolean reorient = age % 2 == 0;   // see VortexEffect: re-face on the interpolation cadence
        for (int i = 0; i < n; i++) {
            double layer = n <= 1 ? 0 : i / (double) (n - 1);
            double rr = radius * (0.35 + layer * 1.1);          // narrow bottom, wide top
            double ang = spin + i * 0.7 + layer * 6;
            // Torn along the spiral, climbing as it turns — the funnel reads as motion, not geometry.
            if (reorient) {
                blocks.get(i).stretch(new Vector(-Math.sin(ang), 0.55, Math.cos(ang)), 2.7f, 0.55f);
            }
            place(i, base.clone().add(Math.cos(ang) * rr, layer * height, Math.sin(ang) * rr));
        }

        Location mid = base.clone().add(0, height * 0.5, 0);
        double suckR = radius * 3.0;   // wide intake — pull enemies in from far
        double coreR = radius * 0.9;   // small core — only here do they get flung
        for (LivingEntity le : nearbyLiving(mid, suckR, null)) {
            Vector flat = base.toVector().subtract(le.getLocation().toVector());
            flat.setY(0);
            double dist = flat.length();
            if (dist <= coreR) {
                // Caught in the eye of the storm: fling up hard (and a touch outward).
                Vector out = dist > 1e-4 ? flat.clone().normalize().multiply(-0.3) : dir.clone().multiply(0.25);
                le.setVelocity(new Vector(out.getX(), 1.15, out.getZ()));
            } else {
                // Outer band: strong horizontal suction toward the funnel, minimal lift.
                Vector in = flat.lengthSquared() > 1e-4 ? flat.normalize().multiply(pull) : new Vector();
                in.setY(0.08);
                le.setVelocity(le.getVelocity().add(in));
            }
        }
        if (age % 8 == 0) WorldFx.damageDot(plugin, mid, radius * 1.4, dmg, player);
        // A moving funnel only leaves a cosmetic wet trail — it never carves or floods terrain
        // along its whole path (that was a heavy lag + grief source).
        if (age % 4 == 0) {
            WorldFx.trail(plugin, mid);
            WorldFx.splash(plugin, base, 10);
            WorldFx.shockwave(plugin, base, radius * 1.3, 18);
        }
        if (age % 16 == 0) ImpactFx.VORTEX.play(plugin, mid, power * 0.6);

        return travelled >= maxDist;
    }
}
