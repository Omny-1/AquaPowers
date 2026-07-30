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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A surging wall of water that rolls forward along the ground (tidal wave / great deluge). */
public final class WaveEffect extends BaseEffect {

    private final Vector dir;
    private final Vector perp;
    private final double width;
    private final double height;
    private final double maxDist;
    private final double speed;
    private final double dmg;
    private final double push;
    private final boolean breakFloor;
    private final int breakDepth;
    private final double power;

    private final Location origin;
    private final int cols;
    private final int rows;
    private final Set<java.util.UUID> hitSet = new HashSet<>();

    private double front = 1.0;

    public WaveEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                      Vector dir, double width, double height, double maxDist, double speed,
                      double dmg, double push, boolean breakFloor, int breakDepth, double power) {
        super(plugin, player, blocks);
        this.power = power;
        Vector d = dir.clone();
        d.setY(0);
        if (d.lengthSquared() < 1e-6) d = new Vector(1, 0, 0);
        this.dir = d.normalize();
        this.perp = new Vector(-this.dir.getZ(), 0, this.dir.getX()).normalize();
        this.width = width;
        this.height = height;
        this.maxDist = maxDist;
        this.speed = speed;
        this.dmg = dmg;
        this.push = push;
        this.breakFloor = breakFloor;
        this.breakDepth = breakDepth;
        this.origin = player.getLocation().clone();
        this.cols = Math.max(2, (int) Math.round(width * 1.5));
        this.rows = Math.max(1, (int) Math.ceil(blocks.size() / (double) cols));
        WorldFx.sound(plugin, origin, "minecraft:entity.player.splash.high_speed", 1.2f, 0.5f);
    }

    @Override
    public boolean tick() {
        age++;
        front += speed;
        if (front > maxDist) return true;

        Location head = origin.clone().add(dir.clone().multiply(front));
        Location ground = Targeting.groundBelow(head.clone().add(0, 1.5, 0), 6);

        for (int i = 0; i < blocks.size(); i++) {
            int c = i % cols;
            int r = i / cols;
            double fx = cols <= 1 ? 0 : (c / (double) (cols - 1) - 0.5) * width;
            double fy = (rows <= 1 ? 0.5 : (r / (double) (rows - 1))) * height;
            double bulge = Math.sin((c / (double) cols) * Math.PI) * 0.4;
            double curl = (fy / Math.max(0.1, height)) * 0.8; // crest leans forward
            Location pos = ground.clone()
                    .add(perp.clone().multiply(fx))
                    .add(0, fy, 0)
                    .add(dir.clone().multiply(bulge + curl));
            // Sheet of water in motion: leaned into the roll, flattened across the front.
            blocks.get(i).stretch(dir.clone().setY(0.35 + curl * 0.5), 1.7f, 0.85f);
            place(i, pos);
        }

        Location frontCenter = ground.clone().add(0, height * 0.4, 0);
        for (LivingEntity le : nearbyLiving(frontCenter, Math.max(1.8, width * 0.55), null)) {
            // Route through WorldFx so damage.enabled / global-multiplier / awakening charge apply
            // here too — this used to be the one ability that ignored all three.
            if (hitSet.add(le.getUniqueId())) WorldFx.hurt(plugin, le, dmg, player);
            le.setVelocity(le.getVelocity().add(dir.clone().multiply(push).setY(0.35)));
        }

        if (breakFloor && age % 3 == 0) {
            WorldFx.impact(plugin, ground, Math.min(width * 0.5, cfg.maxBreakRadius), breakDepth, power);
        }
        if (age % 2 == 0) WorldFx.trail(plugin, frontCenter);
        if (age % 8 == 0) ImpactFx.WAVE.play(plugin, ground, power * 0.5);
        return false;
    }
}
