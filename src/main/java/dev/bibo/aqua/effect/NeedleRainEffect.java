package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Targeting;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Дождь Игл" — a downpour of water needles over an area, not a volley.
 *
 * <p>It used to be a handful of ordinary projectiles fired at once from nine blocks up, which read as
 * a few fat jets arriving together and then nothing. Rain is the opposite shape: it starts high, it
 * is made of many thin streaks, each one barely does anything, and it does not stop. So this holds an
 * area for ten seconds with a constant patter of needles, and the pressure comes from standing in it
 * rather than from any single hit.
 *
 * <p><b>The drops are recycled, not spawned.</b> Each one falls, lands, and is immediately lifted back
 * to the top with a fresh offset. That gives a continuous downpour out of a fixed pool of display
 * entities: the whole effect costs exactly as many entities as the orb it came from, for its entire
 * duration, rather than multiplying them over time.
 */
public final class NeedleRainEffect extends BaseEffect {

    private final Location centre;
    private final double radius;
    private final double height;
    private final int duration;
    private final double dmgPerHit;
    private final double hitRadius;
    private final int hitCooldown;

    private final double[] offX;
    private final double[] offZ;
    private final double[] fall;      // how far this needle has dropped
    private final double[] speed;

    /** Per-victim rate limit — the rain is constant, but not twenty hits in one tick. */
    private final Map<UUID, Long> lastHit = new HashMap<>();

    public NeedleRainEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                            Location centre, double radius, double height, int duration,
                            double dmgPerHit, double hitRadius, int hitCooldown) {
        super(plugin, player, blocks);
        this.centre = centre.clone();
        this.radius = radius;
        this.height = height;
        this.duration = duration;
        this.dmgPerHit = dmgPerHit;
        this.hitRadius = hitRadius;
        this.hitCooldown = hitCooldown;

        int n = blocks.size();
        this.offX = new double[n];
        this.offZ = new double[n];
        this.fall = new double[n];
        this.speed = new double[n];
        for (int i = 0; i < n; i++) {
            reseed(i);
            // Stagger the start so the first drops do not arrive as one sheet.
            fall[i] = ThreadLocalRandom.current().nextDouble() * height;
        }
        WorldFx.sound(plugin, centre, "minecraft:weather.rain", 1.4f, 0.8f);
        WorldFx.sound(plugin, centre, "minecraft:entity.player.splash.high_speed", 0.8f, 1.9f);
    }

    /** Send needle {@code i} back to the clouds at a new spot inside the area. */
    private void reseed(int i) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double a = r.nextDouble() * Math.PI * 2;
        // sqrt keeps the fall evenly spread over the disc instead of crowding the middle.
        double d = radius * Math.sqrt(r.nextDouble());
        offX[i] = Math.cos(a) * d;
        offZ[i] = Math.sin(a) * d;
        fall[i] = 0;
        speed[i] = 1.5 + r.nextDouble() * 1.1;
    }

    @Override
    public boolean tick() {
        age++;
        boolean ending = age > duration;

        for (int i = 0; i < blocks.size(); i++) {
            fall[i] += speed[i];
            double y = height - fall[i];
            Location at = centre.clone().add(offX[i], y, offZ[i]);

            if (y <= 0) {
                land(at.clone().add(0, 0.2, 0));
                if (ending) {
                    // Let the last needles finish falling instead of vanishing mid-air.
                    blocks.get(i).teleport(centre.clone().add(offX[i], -64, offZ[i]));
                    continue;
                }
                reseed(i);
                continue;
            }
            // Thin and long: a needle, not a bolt.
            blocks.get(i).stretch(new Vector(0, -1, 0), 4.2f, 0.22f);
            blocks.get(i).teleport(at);
        }

        if (age % 6 == 0) {
            WorldFx.sound(plugin, centre, "minecraft:weather.rain", 0.9f, 1.1f);
        }
        if (age % 4 == 0) {
            centre.getWorld().spawnParticle(org.bukkit.Particle.FALLING_WATER,
                    centre.clone().add(0, 1.2, 0), 14, radius * 0.8, 0.8, radius * 0.8, 0.0);
        }

        // Everything has fallen out of the bottom and the timer is up.
        return ending && allBelow();
    }

    private boolean allBelow() {
        for (int i = 0; i < blocks.size(); i++) {
            if (height - fall[i] > 0) return false;
        }
        return true;
    }

    /** A needle reaches the ground: a small splash, and a nick for anything standing under it. */
    private void land(Location at) {
        Location ground = Targeting.groundBelow(at.clone().add(0, 1, 0), 4);
        if (age % 3 == 0) WorldFx.trail(plugin, ground);

        long now = plugin.animator().ticks();
        for (LivingEntity le : nearbyLiving(at, hitRadius, null)) {
            Long last = lastHit.get(le.getUniqueId());
            if (last != null && now - last < hitCooldown) continue;
            lastHit.put(le.getUniqueId(), now);
            WorldFx.hurtPeriodic(plugin, le, dmgPerHit, player);
            le.getWorld().spawnParticle(org.bukkit.Particle.SPLASH,
                    le.getLocation().add(0, le.getHeight() * 0.6, 0), 4, 0.25, 0.35, 0.25, 0.01);
        }
        // The map only ever holds whoever is standing in the rain; drop stale entries as we go.
        if (age % 40 == 0) lastHit.entrySet().removeIf(e -> now - e.getValue() > 100);
    }
}
