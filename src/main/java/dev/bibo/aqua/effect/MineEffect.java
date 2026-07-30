package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.fx.ImpactFx;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.Targeting;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/** Deploys several water "mines" on the ground that erupt when an enemy steps near. */
public final class MineEffect extends BaseEffect {

    private final int count;
    private final double triggerRadius;
    private final int duration;
    private final double dmg;
    private final int breakDepth;
    private final double power;

    private final List<List<WaterBlock>> groups;
    private final List<List<Vector>> shapes;
    private final Location[] nodePos;
    private final boolean[] active;

    public MineEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                      Location center, int count, double spreadRadius, double triggerRadius,
                      int duration, double dmg, int breakDepth, double power) {
        super(plugin, player, blocks);
        this.power = power;
        this.count = Math.max(1, count);
        this.triggerRadius = triggerRadius;
        this.duration = duration;
        this.dmg = dmg;
        this.breakDepth = breakDepth;
        this.groups = Geometry.partition(blocks, this.count);
        this.shapes = new java.util.ArrayList<>();
        this.nodePos = new Location[this.count];
        this.active = new boolean[this.count];

        for (int k = 0; k < this.count; k++) {
            double a = (2 * Math.PI * k) / this.count;
            Location spot = center.clone().add(Math.cos(a) * spreadRadius, 1.5, Math.sin(a) * spreadRadius);
            nodePos[k] = Targeting.groundBelow(spot, 8);
            active[k] = true;
            List<Vector> ball = Geometry.ball(groups.get(k).size(), 0.55);
            for (Vector v : ball) v.setY(v.getY() * 0.4 + 0.2);
            shapes.add(ball);
        }
        WorldFx.sound(plugin, center, "minecraft:item.bucket.empty", 1.0f, 1.2f);
    }

    @Override
    public boolean tick() {
        age++;
        boolean anyActive = false;
        for (int k = 0; k < count; k++) {
            if (!active[k]) continue;
            anyActive = true;

            double bob = Math.sin(age * 0.25 + k) * 0.06;
            List<WaterBlock> g = groups.get(k);
            List<Vector> shape = shapes.get(k);
            for (int j = 0; j < g.size(); j++) {
                g.get(j).teleport(nodePos[k].clone().add(shape.get(j).getX(),
                        shape.get(j).getY() + bob, shape.get(j).getZ()));
            }
            if (age % 8 == 0) WorldFx.trail(plugin, nodePos[k]);

            if (!nearbyLiving(nodePos[k], triggerRadius, null).isEmpty()) {
                detonate(k);
            }
        }

        if (!anyActive || age > duration) return true;
        return false;
    }

    private void detonate(int k) {
        Location at = nodePos[k];
        WorldFx.damage(plugin, at, triggerRadius + 1.2, dmg, player, 1.1, 0.3);
        // Smaller, shallower water burst — mines no longer carve big craters or flood the area.
        if (breakDepth > 0) WorldFx.impact(plugin, at, Math.max(1.0, triggerRadius * 0.6), breakDepth, power * 0.5);
        ImpactFx.MINE.play(plugin, at, power);
        for (WaterBlock b : groups.get(k)) b.remove();
        active[k] = false;
    }
}
