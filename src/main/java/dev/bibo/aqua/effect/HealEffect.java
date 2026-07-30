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
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * "Живая Вода" — the half of water-bending that isn't a weapon.
 *
 * <p>The orb unwinds into ribbons that wrap the caster and any allies in reach, mending them over
 * time, putting out fire, and washing off poison and wither. It is the only ability in the kit that
 * points inward, and it is what turns a bender from a siege engine into someone a group wants along.
 *
 * <p>Enemies inside the ribbons get nothing — the water simply passes them by.
 */
public final class HealEffect extends BaseEffect {

    private final double radius;
    private final int duration;
    private final double healPerTick;
    private final double absorption;
    private final int period = 20;

    private final List<Vector> ribbon;
    private final List<LivingEntity> patients = new ArrayList<>();
    private double spin = 0;

    public HealEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                      double radius, int duration, double healPerSecond, double absorption) {
        super(plugin, player, blocks);
        this.radius = radius;
        this.duration = duration;
        this.healPerTick = healPerSecond;
        this.absorption = absorption;
        this.ribbon = Geometry.sphere(blocks.size(), radius * 0.55);
        ImpactFx.HEAL.play(plugin, player.getLocation(), 1.0);
        WorldFx.sound(plugin, player.getLocation(), "minecraft:block.conduit.activate", 1.0f, 1.5f);
    }

    @Override
    public boolean tick() {
        if (!casterValid()) return true;
        age++;
        spin += 0.16;

        Location c = player.getLocation().add(0, 1.0, 0);

        // The ribbons hug the caster and lean out toward whoever is being mended.
        boolean reorient = age % 2 == 0;
        for (int i = 0; i < blocks.size(); i++) {
            Vector off = Geometry.rotateY(ribbon.get(i), spin + i * 0.05);
            double wave = Math.sin(age * 0.14 + i * 0.5) * 0.35;
            Location pos = c.clone().add(off.getX(), off.getY() * 0.5 + wave, off.getZ());
            if (reorient) blocks.get(i).stretch(Geometry.rotateY(off, Math.PI / 2), 2.1f, 0.55f);
            blocks.get(i).teleport(pos);
        }

        if (age % period == 0) {
            patients.clear();
            collectAllies(c);
            for (LivingEntity le : patients) mend(le);
        }

        if (age % 5 == 0) {
            WorldFx.skin(plugin, c, radius * 0.6, 6);
            for (LivingEntity le : patients) {
                if (le.isValid() && !le.isDead()) {
                    WorldFx.stream(plugin, c, le.getLocation().add(0, 1, 0), 3, WorldFx.shallow());
                }
            }
        }

        if (age > duration) {
            ImpactFx.HEAL.play(plugin, c, 1.0);
            return true;
        }
        return false;
    }

    /**
     * Allies = the caster, their tamed animals, and — only on a server where players can't hurt each
     * other — everyone else nearby. Deliberately does not treat "is also a bender" as friendly: on a
     * PvP server the other water-mage is usually the person trying to kill you.
     */
    private void collectAllies(Location c) {
        patients.add(player);
        if (c.getWorld() == null) return;
        boolean pvpOff = !plugin.cfg().friendlyFire;
        for (Entity e : c.getWorld().getNearbyEntities(c, radius, radius, radius)) {
            if (!(e instanceof LivingEntity le) || e.equals(player) || le.isDead()) continue;
            if (c.distance(e.getLocation()) > radius) continue;
            boolean ally = e instanceof Player
                    ? pvpOff
                    : e instanceof org.bukkit.entity.Tameable t && player.equals(t.getOwner());
            if (ally) patients.add(le);
        }
    }

    private void mend(LivingEntity le) {
        try {
            double max = le.getAttribute(Attribute.MAX_HEALTH) != null
                    ? le.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
            if (le.getHealth() < max) {
                le.setHealth(Math.min(max, le.getHealth() + healPerTick));
                le.getWorld().spawnParticle(org.bukkit.Particle.HEART,
                        le.getLocation().add(0, le.getHeight() + 0.3, 0), 1, 0.2, 0.1, 0.2, 0.0);
            }
            // Water puts fire out and washes the blood out of your system.
            if (le.getFireTicks() > 0) {
                le.setFireTicks(0);
                WorldFx.steam(plugin, le.getLocation().add(0, 1, 0), 10);
            }
            le.removePotionEffect(PotionEffectType.POISON);
            le.removePotionEffect(PotionEffectType.WITHER);
            le.removePotionEffect(PotionEffectType.NAUSEA);
            le.removePotionEffect(PotionEffectType.BLINDNESS);
            if (absorption > 0 && le.getAbsorptionAmount() < absorption) {
                le.setAbsorptionAmount(absorption);
            }
        } catch (Exception ignored) {
        }
    }
}
