package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.fx.ImpactFx;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/** Swirling water construct: a ground whirlpool, or a prison cage around a victim. */
public final class VortexEffect extends BaseEffect {

    // DOME was removed along with "Water Dome" — it duplicated Water Barrier.
    public enum Mode { WHIRLPOOL, PRISON }

    private final Mode mode;
    private final Location fixedCenter;
    private final LivingEntity target;
    private final double radius;
    private final int duration;
    private final double pull;
    private final double dmg;
    private final double height;
    private final double power;
    private final List<Vector> sphereOffsets;
    private double spin = 0;

    public VortexEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks, Mode mode,
                        Location center, LivingEntity target, double radius, int duration,
                        double pull, double dmg, double height, double power) {
        super(plugin, player, blocks);
        this.power = power;
        this.mode = mode;
        this.fixedCenter = center == null ? player.getLocation() : center.clone();
        this.target = target;
        this.radius = radius;
        this.duration = duration;
        this.pull = pull;
        this.dmg = dmg;
        this.height = height;
        this.sphereOffsets = Geometry.sphere(blocks.size(), radius);
        WorldFx.sound(plugin, fixedCenter, "minecraft:block.bubble_column.whirlpool_inside", 1.0f, 0.8f);
    }

    private Location currentCenter() {
        return mode == Mode.PRISON && target != null
                ? target.getLocation().add(0, target.getHeight() * 0.5, 0)
                : fixedCenter.clone();
    }

    @Override
    public boolean tick() {
        age++;
        spin += 0.35;
        if (mode == Mode.PRISON && (target == null || target.isDead() || !target.isValid())) return true;

        Location c = currentCenter();
        int n = blocks.size();

        switch (mode) {
            case WHIRLPOOL -> {
                // A fast spin turns every drop's facing every tick. Re-orienting on the interpolation
                // cadence instead of every tick halves the transform packets for a 6-second funnel
                // and is indistinguishable at this rotation speed.
                boolean reorient = age % 2 == 0;
                for (int i = 0; i < n; i++) {
                    double layer = n <= 1 ? 0 : i / (double) (n - 1);
                    double rr = radius * (1 - layer * 0.7);
                    double ang = spin + i * 0.6;
                    // Streaked along the swirl, so the funnel shows its rotation instead of stuttering.
                    if (reorient) {
                        blocks.get(i).stretch(new Vector(-Math.sin(ang), 0.18, Math.cos(ang)), 2.3f, 0.6f);
                    }
                    place(i, c.clone().add(Math.cos(ang) * rr, layer * height, Math.sin(ang) * rr));
                }
                for (LivingEntity le : nearbyLiving(c, radius * 1.5, null)) {
                    Vector v = c.toVector().subtract(le.getLocation().toVector());
                    v.setY(0);
                    if (v.lengthSquared() > 1e-4) v.normalize().multiply(pull).setY(-0.05);
                    le.setVelocity(le.getVelocity().add(v));
                }
                if (age % 10 == 0) WorldFx.damageDot(plugin, c, radius, dmg, player);
            }
            case PRISON -> {
                for (int i = 0; i < n; i++) {
                    place(i, c.clone().add(Geometry.rotateY(sphereOffsets.get(i), spin * 0.3)));
                }
                if (target != null) {
                    applyPotion(target, PotionEffectType.SLOWNESS, 2);
                    target.setVelocity(target.getVelocity().multiply(0.2));
                    if (age % 10 == 0) {
                        WorldFx.hurtPeriodic(plugin, target, dmg, player);
                    }
                }
            }
        }

        if (age % 12 == 0) WorldFx.trail(plugin, c);
        if (age % 10 == 0) WorldFx.skin(plugin, c, radius, 5);
        if (age > duration) {
            ImpactFx.VORTEX.play(plugin, c, power);
            if (mode == Mode.WHIRLPOOL) WorldFx.impact(plugin, c, radius * 0.7, 2, power);
            return true;
        }
        return false;
    }

    private void applyPotion(LivingEntity le, PotionEffectType type, int amp) {
        try {
            le.addPotionEffect(new PotionEffect(type, 30, amp, false, false));
        } catch (Exception ignored) {
        }
    }
}
