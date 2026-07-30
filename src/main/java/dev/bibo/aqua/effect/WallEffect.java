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

/** A wall of water that rises in front of the caster, holds as a barrier, then surges forward. */
public final class WallEffect extends BaseEffect {

    private final Vector dir;
    private final Vector perp;
    private final double width;
    private final double height;
    private final double dmg;
    private final double push;
    private final int breakDepth;
    private final double power;

    private final Location origin;
    private final int cols;
    private final int rows;
    private final int rise = 12;
    private final int hold;
    private final int surge = 16;
    private final double standDist = 2.5;
    private final double surgeDist = 6;
    private boolean impacted = false;

    public WallEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                      double width, double height, double dmg, double push, int breakDepth,
                      int holdTicks, double power) {
        super(plugin, player, blocks);
        Vector d = player.getEyeLocation().getDirection();
        d.setY(0);
        if (d.lengthSquared() < 1e-6) d = new Vector(1, 0, 0);
        this.dir = d.normalize();
        this.perp = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        this.width = width;
        this.height = height;
        this.dmg = dmg;
        this.push = push;
        this.breakDepth = breakDepth;
        this.power = power;
        this.hold = Math.max(10, holdTicks);
        this.origin = player.getLocation().clone();
        this.cols = Math.max(2, (int) Math.round(width * 1.5));
        this.rows = Math.max(1, (int) Math.ceil(blocks.size() / (double) cols));
        WorldFx.sound(plugin, origin, "minecraft:item.bucket.empty", 1.2f, 0.6f);
    }

    @Override
    public boolean tick() {
        age++;
        double dist;
        boolean surging = false;
        if (age <= rise) {
            dist = standDist;
        } else if (age <= rise + hold) {
            dist = standDist;
        } else {
            surging = true;
            double t = (age - rise - hold) / (double) surge;
            dist = standDist + surgeDist * Math.min(1, t);
        }

        double riseT = Math.min(1.0, age / (double) rise);
        Location head = origin.clone().add(dir.clone().multiply(dist));
        Location ground = Targeting.groundBelow(head.clone().add(0, 1.5, 0), 6);

        // Rising: columns of water climbing. Surging: slabs thrown forward.
        Vector grain = surging ? dir.clone().setY(0.3) : new Vector(0, 1, 0);
        for (int i = 0; i < blocks.size(); i++) {
            int c = i % cols;
            int r = i / cols;
            double fx = cols <= 1 ? 0 : (c / (double) (cols - 1) - 0.5) * width;
            double fy = (rows <= 1 ? 0.5 : (r / (double) (rows - 1))) * height * riseT;
            blocks.get(i).stretch(grain, surging ? 2.0f : 2.4f, surging ? 0.8f : 0.7f);
            place(i, ground.clone().add(perp.clone().multiply(fx)).add(0, fy, 0));
        }

        Location mid = ground.clone().add(0, height * 0.5, 0);
        for (LivingEntity le : nearbyLiving(mid, Math.max(2.0, width * 0.6), null)) {
            Vector away = le.getLocation().toVector().subtract(origin.toVector());
            away.setY(0);
            double along = away.dot(dir);
            Vector v = dir.clone().multiply(along >= 0 ? push : -push * 0.6).setY(surging ? 0.4 : 0.2);
            le.setVelocity(le.getVelocity().add(v));
            if (surging) WorldFx.hurt(plugin, le, dmg, player);
        }

        if (age % 4 == 0) WorldFx.trail(plugin, mid);

        if (surging && !impacted && age >= rise + hold + surge) {
            WorldFx.impact(plugin, ground, Math.max(2.0, width * 0.4), breakDepth, power);
            ImpactFx.WALL.play(plugin, ground, power);
            impacted = true;
            return true;
        }
        return false;
    }
}
