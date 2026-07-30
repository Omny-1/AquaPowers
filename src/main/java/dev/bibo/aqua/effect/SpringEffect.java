package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.fx.ImpactFx;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * "Spring" — a fountain planted in the ground that holds a piece of ground.
 *
 * <p>Where {@link HealEffect} follows the caster, this stays put: allies who stand in it are mended
 * and can breathe underwater; enemies who wade in are slowed and pushed back out. It gives a bender
 * a reason to choose <i>where</i> a fight happens, which nothing else in the kit did.
 */
public final class SpringEffect extends BaseEffect {

    private final Location base;
    private final double radius;
    private final int duration;
    private final double healPerSecond;
    private final double slowPush;

    private final List<Vector> jets;
    private double phase = 0;

    public SpringEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                        Location ground, double radius, int duration, double healPerSecond, double slowPush) {
        super(plugin, player, blocks);
        this.base = ground.clone();
        this.radius = radius;
        this.duration = duration;
        this.healPerSecond = healPerSecond;
        this.slowPush = slowPush;
        this.jets = Geometry.column(blocks.size(), radius * 0.5, radius * 1.1);
        ImpactFx.HEAL.play(plugin, base, 1.0);
        WorldFx.sound(plugin, base, "minecraft:block.bubble_column.upwards_inside", 1.2f, 1.1f);
    }

    @Override
    public boolean tick() {
        age++;
        phase += 0.13;

        // Water climbs the fountain and falls back — each drop cycles up its own jet.
        for (int i = 0; i < blocks.size(); i++) {
            Vector j = jets.get(i);
            double t = ((phase + i * 0.11) % 1.0);
            double lift = Math.sin(t * Math.PI) * radius * 1.15;
            double spread = 0.35 + t * 1.1;
            Location pos = base.clone().add(j.getX() * spread, lift, j.getZ() * spread);
            WaterBlock b = blocks.get(i);
            b.stretch(new Vector(0, Math.cos(t * Math.PI), 0).add(new Vector(j.getX(), 0, j.getZ()).multiply(0.35)),
                    1.9f, 0.6f);
            b.teleport(pos);
        }

        if (age % 20 == 0) affectOccupants();

        if (age % 4 == 0) {
            WorldFx.skin(plugin, base.clone().add(0, radius * 0.5, 0), radius * 0.8, 6);
            WorldFx.shockwave(plugin, base, radius * (0.6 + 0.4 * Math.sin(phase)), 18);
        }
        if (age % 10 == 0) WorldFx.drip(plugin, base.clone().add(0, 0.4, 0));

        if (age > duration) {
            WorldFx.splash(plugin, base, 40);
            WorldFx.sound(plugin, base, "minecraft:block.bubble_column.whirlpool_inside", 1.0f, 0.7f);
            return true;
        }
        return false;
    }

    private void affectOccupants() {
        if (base.getWorld() == null) return;
        for (Entity e : base.getWorld().getNearbyEntities(base, radius, radius * 1.4, radius)) {
            if (!(e instanceof LivingEntity le) || le.isDead()) continue;
            if (base.distance(e.getLocation()) > radius) continue;

            // Same rule as Living Water: yourself, your pets, and everyone else only where PvP is off.
            boolean ally = e.equals(player)
                    || (e instanceof Player && !plugin.cfg().friendlyFire)
                    || (e instanceof org.bukkit.entity.Tameable t && player.equals(t.getOwner()));

            if (ally) {
                try {
                    double max = le.getAttribute(Attribute.MAX_HEALTH) != null
                            ? le.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
                    if (le.getHealth() < max) {
                        le.setHealth(Math.min(max, le.getHealth() + healPerSecond));
                    }
                    le.setFireTicks(0);
                    le.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 100, 0, false, false));
                    le.getWorld().spawnParticle(org.bukkit.Particle.HEART,
                            le.getLocation().add(0, le.getHeight() + 0.3, 0), 1, 0.2, 0.1, 0.2, 0.0);
                } catch (Exception ignored) {
                }
            } else if (plugin.cfg().damageEnabled) {
                // Enemies find it hard going: waist-deep water that actively pushes back.
                try {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
                } catch (Exception ignored) {
                }
                Vector out = le.getLocation().toVector().subtract(base.toVector());
                out.setY(0);
                if (out.lengthSquared() > 1e-4) {
                    le.setVelocity(le.getVelocity().add(out.normalize().multiply(slowPush).setY(0.12)));
                }
            }
        }
    }
}
