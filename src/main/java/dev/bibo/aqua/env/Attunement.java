package dev.bibo.aqua.env;

import dev.bibo.aqua.AquaConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Where the bender is standing, and what that does to their water.
 *
 * <p>Before this, the surroundings only ever changed how fast stamina refilled — you bent exactly the
 * same way waist-deep in a river as you did in the middle of the Nether. Now the environment is a
 * live parameter of every cast: the element is generous where there is water and grudging where there
 * is none.
 *
 * <p>It deliberately pulls <i>against</i> the existing stamina curve rather than with it. Standing in
 * an ocean makes casts cheap and strong but refills the bar slowly; standing on lava refills it in
 * five seconds but every cast costs nearly double and lands soft. Neither place is simply better —
 * they play differently, which is the point.
 *
 * <p>Detection reads the biome's own temperature and sky access rather than matching biome names, so
 * it works on modded/datapack biomes and on anything Mojang adds later.
 */
public enum Attunement {

    /** In the water. The element is all around you. */
    SUBMERGED("Immersion", "&b", 0.55, 1.30, 0.0),
    /** Rain from an open sky — you can pull water out of the air itself. */
    RAIN("Downpour", "&9", 0.70, 1.18, 0.0),
    /** Standing beside a body of water. */
    FLOW("Flow", "&3", 0.80, 1.12, 0.0),
    /** Ordinary ground. */
    NEUTRAL("Balance", "&7", 1.0, 1.0, 0.0),
    /** Desert, savanna, badlands — the air drinks your orb. */
    DRY("Drought", "&e", 1.30, 0.90, 0.6),
    /** Lava, fire, the Nether. Water here is a losing argument. */
    SCORCHED("Inferno", "&c", 1.70, 0.78, 2.2);

    public final String label;
    public final String color;
    /** Multiplier on every ability's stamina cost. */
    public final double costMult;
    /** Multiplier on effect power — size, damage and aftermath all scale off it. */
    public final double powerMult;
    /** Water blocks the floating orb loses per second to evaporation. */
    public final double evaporatePerSec;

    Attunement(String label, String color, double costMult, double powerMult, double evaporatePerSec) {
        this.label = label;
        this.color = color;
        this.costMult = costMult;
        this.powerMult = powerMult;
        this.evaporatePerSec = evaporatePerSec;
    }

    /** Water is available from the surroundings even with no source blocks in reach. */
    public boolean freeWater() {
        return this == SUBMERGED || this == RAIN;
    }

    public String display() {
        return color + label;
    }

    /**
     * Seconds to refill the stamina bar here. Keeps the existing config keys meaningful — dry and
     * hot places still refill fastest, which is what balances their punishing cast costs.
     */
    public double secondsToFull(AquaConfig cfg) {
        return switch (this) {
            case SUBMERGED, FLOW -> cfg.secWater;
            case RAIN -> (cfg.secWater + cfg.secGround) * 0.5;
            case NEUTRAL -> cfg.secGround;
            case DRY -> cfg.secSand;
            case SCORCHED -> cfg.secLava;
        };
    }

    /** One scan of the player's surroundings, classified. Callers should cache this — see HydroUser. */
    public static Attunement of(Player p, AquaConfig cfg) {
        if (p.isInWater()) return SUBMERGED;

        World w = p.getWorld();
        Location c = p.getLocation();
        Block feet = c.getBlock();
        if (feet.getType() == Material.WATER) return SUBMERGED;

        double temp;
        double humidity;
        try {
            temp = feet.getTemperature();
            humidity = feet.getHumidity();
        } catch (Throwable t) {
            temp = 0.8;      // temperature is undefined above/below the world; treat as temperate
            humidity = 0.5;
        }

        // The scan used to sweep (2R+1)^3 — 2197 blocks at the default radius — every two seconds for
        // every powered player, and half of it was dead weight: the sand tally only fed a test that
        // the temperature check above already covered. It now walks a smaller sphere and bails as
        // soon as it has seen enough water to call it, which is the common case near any shoreline.
        int R = Math.min(4, cfg.staminaScanRadius);
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
        int minY = w.getMinHeight(), maxY = w.getMaxHeight();
        final int wetEnough = 25;
        int water = 0, heat = 0, solid = 0;
        outer:
        for (int dy = -R; dy <= R; dy++) {
            int y = cy + dy;
            if (y < minY || y >= maxY) continue;
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    if (dx * dx + dy * dy + dz * dz > R * R) continue;
                    Material t = w.getBlockAt(cx + dx, y, cz + dz).getType();
                    if (t.isAir()) continue;
                    solid++;
                    if (t == Material.WATER) {
                        if (++water >= wetEnough) break outer;
                    } else if (t == Material.LAVA || t == Material.FIRE || t == Material.SOUL_FIRE
                            || t == Material.CAMPFIRE || t == Material.SOUL_CAMPFIRE
                            || t == Material.MAGMA_BLOCK) {
                        heat++;
                        break outer;   // one hot block is already the answer
                    }
                }
            }
        }

        if (heat >= 1 || temp >= 1.5) return SCORCHED;
        if (water >= wetEnough || (solid > 0 && water * 2 >= solid)) return FLOW;
        // DRY is tested before RAIN on purpose. `hasStorm()` is a property of the world, not of the
        // biome, so a savanna (temp 1.2, no precipitation in vanilla) used to be handed full Downpour
        // benefits while standing under a clear sky. Anything hot or arid is dry, storm or not; the
        // humidity term additionally excludes rainless biomes that happen to be temperate.
        if (temp >= 0.95 || humidity < 0.25) return DRY;
        if (w.hasStorm() && feet.getLightFromSky() == 15) return RAIN;
        return NEUTRAL;
    }
}
