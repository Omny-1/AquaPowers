package dev.bibo.aqua.fx;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

/**
 * The signature of a hit. Every ability used to end in the same crater, the same two splash sounds
 * and the same particle burst, so a pistol-shot of water and a falling meteor were indistinguishable
 * once they landed. Each entry here gives one ability its own voice — pitch, texture and silhouette.
 */
public enum ImpactFx {

    /** Thin, fast, precise. A puncture, not an explosion. */
    SPEAR {
        @Override
        public void play(AquaWaterPlugin plugin, Location at, double power) {
            World w = at.getWorld();
            if (w == null) return;
            WorldFx.sound(plugin, at, "minecraft:entity.player.splash.high_speed", 0.9f, 1.6f);
            WorldFx.sound(plugin, at, "minecraft:block.pointed_dripstone.drip_water_into_cauldron", 1.0f, 0.6f);
            fx(w, Particle.SPLASH, at, (int) (14 + 16 * power), 0.25, 0.35);
            WorldFx.skin(plugin, at, 1.1 + power, 6);
        }
    },

    /**
     * Many small hits at once — a rattle, not a boom. A full-charge blast lands up to nine pellets
     * within a couple of ticks, so this one throttles its own audio: the particles always play, the
     * sounds only from the first pellet to arrive at a given spot. Eighteen stacked copies of the
     * same sample is not louder, it is mud.
     */
    SHOTGUN {
        @Override
        public void play(AquaWaterPlugin plugin, Location at, double power) {
            World w = at.getWorld();
            if (w == null) return;
            if (claimAudio(plugin, at)) {
                WorldFx.sound(plugin, at, "minecraft:entity.generic.splash", 0.8f, 1.5f);
                WorldFx.sound(plugin, at, "minecraft:block.bubble_column.bubble_pop", 1.0f, 1.2f);
            }
            fx(w, Particle.SPLASH, at, (int) (10 + 14 * power), 0.6, 0.3);
            fx(w, Particle.BUBBLE_POP, at, 10, 0.7, 0.1);
        }
    },

    /** A shower from above — light and stinging. */
    NEEDLE {
        @Override
        public void play(AquaWaterPlugin plugin, Location at, double power) {
            World w = at.getWorld();
            if (w == null) return;
            WorldFx.sound(plugin, at, "minecraft:block.pointed_dripstone.drip_water", 1.0f, 1.8f);
            fx(w, Particle.FALLING_WATER, at, (int) (8 + 10 * power), 0.4, 0.0);
            fx(w, Particle.SPLASH, at, 8, 0.3, 0.2);
        }
    },

    /** A lash — the crack comes first, the water second. */
    WHIP {
        @Override
        public void play(AquaWaterPlugin plugin, Location at, double power) {
            World w = at.getWorld();
            if (w == null) return;
            WorldFx.sound(plugin, at, "minecraft:entity.player.attack.sweep", 1.1f, 1.4f);
            WorldFx.sound(plugin, at, "minecraft:item.trident.riptide_1", 0.7f, 1.7f);
            fx(w, Particle.SWEEP_ATTACK, at, 2, 0.3, 0.0);
            fx(w, Particle.SPLASH, at, (int) (12 + 12 * power), 0.5, 0.25);
        }
    },

    /** Pressure from below. Low, wet, sustained. */
    GEYSER {
        @Override
        public void play(AquaWaterPlugin plugin, Location at, double power) {
            World w = at.getWorld();
            if (w == null) return;
            WorldFx.sound(plugin, at, "minecraft:block.bubble_column.upwards_inside", 1.4f, 0.6f);
            WorldFx.sound(plugin, at, "minecraft:entity.player.splash.high_speed", 1.0f, 0.5f);
            fx(w, Particle.BUBBLE_COLUMN_UP, at, (int) (30 + 40 * power), 0.5, 0.4);
            WorldFx.shockwave(plugin, at, 1.6 + 2.0 * power, 22);
        }
    },

    /** The heaviest single hit in the kit — it should feel like the ground moved. */
    METEOR {
        @Override
        public void play(AquaWaterPlugin plugin, Location at, double power) {
            World w = at.getWorld();
            if (w == null) return;
            WorldFx.sound(plugin, at, "minecraft:entity.generic.explode", 1.4f, 0.55f);
            WorldFx.sound(plugin, at, "minecraft:entity.player.splash.high_speed", 1.4f, 0.4f);
            WorldFx.sound(plugin, at, "minecraft:block.anvil.land", 0.6f, 0.5f);
            fx(w, Particle.EXPLOSION_EMITTER, at, 1, 0.0, 0.0);
            WorldFx.spray(plugin, at, (int) (40 + 60 * power));
            for (int ring = 1; ring <= 3; ring++) {
                WorldFx.shockwave(plugin, at, ring * (1.6 + 2.2 * power), 28);
            }
        }
    },

    /** A rolling mass. Continuous rumble rather than a single strike. */
    WAVE {
        @Override
        public void play(AquaWaterPlugin plugin, Location at, double power) {
            World w = at.getWorld();
            if (w == null) return;
            WorldFx.sound(plugin, at, "minecraft:entity.player.splash.high_speed", 1.5f, 0.35f);
            WorldFx.sound(plugin, at, "minecraft:block.water.ambient", 1.5f, 0.5f);
            WorldFx.spray(plugin, at, (int) (26 + 34 * power));
            WorldFx.shockwave(plugin, at, 2.5 + 3.0 * power, 30);
        }
    },

    /** Suction — inward, droning, no percussion. */
    VORTEX {
        @Override
        public void play(AquaWaterPlugin plugin, Location at, double power) {
            World w = at.getWorld();
            if (w == null) return;
            WorldFx.sound(plugin, at, "minecraft:block.bubble_column.whirlpool_inside", 1.3f, 0.5f);
            fx(w, Particle.CURRENT_DOWN, at, (int) (20 + 25 * power), 0.8, 0.05);
            WorldFx.skin(plugin, at, 1.8 + power, 12);
        }
    },

    /** A trap springing. Sharp, close, surprising. */
    MINE {
        @Override
        public void play(AquaWaterPlugin plugin, Location at, double power) {
            World w = at.getWorld();
            if (w == null) return;
            WorldFx.sound(plugin, at, "minecraft:block.bubble_column.upwards_inside", 1.2f, 1.5f);
            WorldFx.sound(plugin, at, "minecraft:entity.generic.explode", 0.5f, 1.8f);
            fx(w, Particle.SPLASH, at, (int) (20 + 20 * power), 0.4, 0.5);
            WorldFx.shockwave(plugin, at, 1.4, 16);
        }
    },

    /** A wall coming down. Slabs of water, not spray. */
    WALL {
        @Override
        public void play(AquaWaterPlugin plugin, Location at, double power) {
            World w = at.getWorld();
            if (w == null) return;
            WorldFx.sound(plugin, at, "minecraft:item.bucket.empty", 1.4f, 0.45f);
            WorldFx.sound(plugin, at, "minecraft:entity.player.splash.high_speed", 1.0f, 0.6f);
            fx(w, Particle.FALLING_WATER, at, (int) (24 + 24 * power), 1.2, 0.0);
            WorldFx.shockwave(plugin, at, 2.0 + power, 20);
        }
    },

    /** Restoration. Warm, chiming, upward. */
    HEAL {
        @Override
        public void play(AquaWaterPlugin plugin, Location at, double power) {
            World w = at.getWorld();
            if (w == null) return;
            WorldFx.sound(plugin, at, "minecraft:block.amethyst_block.chime", 1.0f, 1.4f);
            WorldFx.sound(plugin, at, "minecraft:block.conduit.ambient", 0.8f, 1.6f);
            fx(w, Particle.HEART, at.clone().add(0, 1.1, 0), 3, 0.4, 0.02);
            fx(w, Particle.GLOW, at, 14, 0.5, 0.04);
            WorldFx.skin(plugin, at.clone().add(0, 1, 0), 1.4, 10);
        }
    },

    /** Everything, at once. */
    NUKE {
        @Override
        public void play(AquaWaterPlugin plugin, Location at, double power) {
            World w = at.getWorld();
            if (w == null) return;
            WorldFx.sound(plugin, at, "minecraft:entity.generic.explode", 1.6f, 0.35f);
            WorldFx.sound(plugin, at, "minecraft:entity.lightning_bolt.thunder", 1.6f, 0.5f);
            WorldFx.sound(plugin, at, "minecraft:entity.warden.sonic_boom", 1.4f, 0.5f);
            fx(w, Particle.FLASH, at, 4, 0.0, 0.0);
            fx(w, Particle.EXPLOSION_EMITTER, at, 6, 3.0, 0.0);
            for (int ring = 1; ring <= 6; ring++) WorldFx.shockwave(plugin, at, ring * 4.0, 46);
            WorldFx.spray(plugin, at, 160);
        }
    };

    public abstract void play(AquaWaterPlugin plugin, Location at, double power);

    // ---- audio rate limit ---------------------------------------------------
    // Coarse grid of "somewhere a sound just played", so a volley of projectiles landing together
    // does not multiply one impact's audio by the number of pellets.
    private static final Map<Long, Long> RECENT_AUDIO = new HashMap<>();
    private static final int GRID = 3;          // blocks
    private static final long QUIET_TICKS = 4;

    /** @return true if this spot may make noise now. Main thread only, like everything here. */
    private static boolean claimAudio(AquaWaterPlugin plugin, Location at) {
        long now = plugin.animator().ticks();
        long cell = (((long) Math.floorDiv(at.getBlockX(), GRID)) & 0x1FFFFF) << 42
                | (((long) Math.floorDiv(at.getBlockY(), GRID)) & 0x1FFFFF) << 21
                | (((long) Math.floorDiv(at.getBlockZ(), GRID)) & 0x1FFFFF);
        Long last = RECENT_AUDIO.get(cell);
        if (last != null && now - last < QUIET_TICKS) return false;
        if (RECENT_AUDIO.size() > 512) RECENT_AUDIO.entrySet().removeIf(e -> now - e.getValue() > 40);
        RECENT_AUDIO.put(cell, now);
        return true;
    }

    private static void fx(World w, Particle p, Location at, int n, double spread, double speed) {
        try {
            w.spawnParticle(p, at, n, spread, spread, spread, speed);
        } catch (Exception ignored) {
        }
    }
}
