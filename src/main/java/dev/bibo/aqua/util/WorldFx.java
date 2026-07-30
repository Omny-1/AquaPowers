package dev.bibo.aqua.util;

import dev.bibo.aqua.AquaConfig;
import dev.bibo.aqua.AquaWaterPlugin;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** World-side consequences: damage, terrain breaking, water spill (wall/floor aware), particles, sounds. */
public final class WorldFx {

    private WorldFx() {}

    private static final Color DEEP = Color.fromRGB(28, 96, 190);
    private static final Color SHALLOW = Color.fromRGB(122, 205, 255);
    private static final Color FOAM = Color.fromRGB(226, 246, 255);

    // ---- Damage -------------------------------------------------------------

    /**
     * The single "may water hit this?" rule, shared by every effect and every AoE.
     * Skips the caster, decorations (armour stands), corpses, invulnerable mobs and
     * players who can't be hit anyway (spectator/creative), plus friendly-fire opt-out.
     */
    public static boolean targetable(AquaConfig cfg, Entity e, Player source) {
        if (!(e instanceof LivingEntity le)) return false;
        if (e.equals(source) || e instanceof ArmorStand) return false;
        if (le.isDead() || !le.isValid() || le.isInvulnerable()) return false;
        if (e instanceof Player pl) {
            if (!cfg.friendlyFire) return false;
            if (pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE) return false;
        }
        return true;
    }

    /**
     * Deal damage and feed the caster's awakening meter. Applies the global damage multiplier.
     *
     * <p>Awakening is credited with the damage that <b>actually landed</b>, measured from the
     * victim's health, not with the amount we asked for. Crediting the request meant a bender could
     * charge the meter at full rate by hitting a heavily armoured target, something immune, or
     * anything whose damage another plugin cancelled — the bar filled from swings that did nothing.
     */
    public static void hurt(AquaWaterPlugin plugin, LivingEntity le, double dmg, Player source) {
        if (!plugin.cfg().damageEnabled) return;
        double d = dmg * plugin.cfg().damageMultiplier;
        double before = le.getHealth() + le.getAbsorptionAmount();
        try {
            le.damage(d, source);
        } catch (Exception ignored) {
            return;
        }
        double dealt = before - (le.getHealth() + le.getAbsorptionAmount());
        if (source != null && dealt > 0) plugin.users().onDamageDealt(source, dealt);
    }

    /**
     * Damage from an effect that ticks faster than vanilla's 20-tick invulnerability window
     * (whirlpool, prison, clones). Without clearing the window most of those ticks are silently
     * swallowed and the ability deals a fraction of its listed damage.
     */
    public static void hurtPeriodic(AquaWaterPlugin plugin, LivingEntity le, double dmg, Player source) {
        if (!plugin.cfg().damageEnabled) return;
        le.setNoDamageTicks(0);
        hurt(plugin, le, dmg, source);
    }

    /**
     * How much of a blast reaches a target behind cover.
     *
     * <p>Every area effect in this plugin used to be a plain box query plus a distance check, so a
     * meteor killed someone sealed inside a bunker five blocks away. A hard line-of-sight test is
     * wrong too — water goes round corners. So cover attenuates instead of blocking: full damage in
     * the open, a configurable fraction through a wall.
     */
    private static double coverFactor(AquaConfig cfg, Location center, LivingEntity le) {
        if (cfg.coverDamage >= 1.0) return 1.0;
        World w = center.getWorld();
        if (w == null || !w.equals(le.getWorld())) return cfg.coverDamage;
        Location eye = le.getEyeLocation();
        Vector to = eye.toVector().subtract(center.toVector());
        double dist = to.length();
        if (dist < 0.6) return 1.0;
        try {
            var hit = w.rayTraceBlocks(center, to.normalize(), dist - 0.3,
                    FluidCollisionMode.NEVER, true);
            return hit == null ? 1.0 : cfg.coverDamage;
        } catch (Exception e) {
            return 1.0;
        }
    }

    public static void damage(AquaWaterPlugin plugin, Location center, double radius,
                              double dmg, Player source, double knockUp, double knockOut) {
        damage(plugin, center, radius, dmg, source, knockUp, knockOut, null);
    }

    /**
     * Area damage, optionally skipping one entity.
     *
     * <p>The exclusion exists because a projectile that strikes a target then splashes its
     * neighbours was catching that same target in its own splash: vanilla's invulnerability window
     * swallowed the second, smaller hit, but the knockback and the awakening credit were applied
     * twice regardless.
     */
    public static void damage(AquaWaterPlugin plugin, Location center, double radius,
                              double dmg, Player source, double knockUp, double knockOut,
                              LivingEntity skip) {
        AquaConfig cfg = plugin.cfg();
        if (!cfg.damageEnabled || center.getWorld() == null) return;
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!targetable(cfg, e, source)) continue;
            if (skip != null && e.equals(skip)) continue;
            if (center.distance(e.getLocation()) > radius) continue;
            LivingEntity le = (LivingEntity) e;
            double f = coverFactor(cfg, center, le);
            hurt(plugin, le, dmg * f, source);
            Vector kb = e.getLocation().toVector().subtract(center.toVector());
            if (kb.lengthSquared() < 1.0e-4) {
                kb = source != null ? source.getEyeLocation().getDirection().clone() : new Vector(0, 1, 0);
            }
            kb.normalize().multiply(knockOut * f).setY(knockUp * f);
            e.setVelocity(e.getVelocity().add(kb));
        }
    }

    /** Periodic, no-knockback damage (prison / vortex / whirlpool). */
    public static void damageDot(AquaWaterPlugin plugin, Location center, double radius, double dmg, Player source) {
        AquaConfig cfg = plugin.cfg();
        if (!cfg.damageEnabled || center.getWorld() == null) return;
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!targetable(cfg, e, source)) continue;
            if (center.distance(e.getLocation()) > radius) continue;
            LivingEntity le = (LivingEntity) e;
            hurtPeriodic(plugin, le, dmg * coverFactor(cfg, center, le), source);
        }
    }

    // ---- Terrain edits ------------------------------------------------------

    /**
     * A batch of pending terrain changes. Nothing touches the world until {@link #commit} has run the
     * whole batch past {@link Protect}, so a claim can veto an impact wholesale or block by block —
     * and the undo snapshot only ever records blocks we were actually allowed to change.
     */
    private static final class Edits {
        final List<Block> toAir = new ArrayList<>();
        final List<Block> toWater = new ArrayList<>();
        /** Topmost water of each column — the only layer that gets a physics update. */
        final Set<Block> waterSurface = new HashSet<>();
        BlockData sample;
        /** Set once the batch has actually landed, so particles can't advertise a vetoed impact. */
        BlockData appliedSample;

        void air(Block b, AquaConfig cfg) {
            if (!breakable(b, cfg)) return;
            if (sample == null) sample = b.getBlockData();
            toAir.add(b);
        }

        /** Replace a solid block with water (carving a bowl). */
        void water(Block b, AquaConfig cfg, boolean surface) {
            if (!breakable(b, cfg)) return;
            if (sample == null) sample = b.getBlockData();
            toWater.add(b);
            if (surface) waterSurface.add(b);
        }

        /** Pour water into an empty block. Separate from {@link #water} because `breakable` rejects
         *  air by design, and both callers here are filling air rather than breaking anything. */
        void fill(Block b, AquaConfig cfg, boolean surface) {
            if (!b.isEmpty() || cfg.blacklist.contains(b.getType())) return;
            toWater.add(b);
            if (surface) waterSurface.add(b);
        }

        boolean isEmpty() {
            return toAir.isEmpty() && toWater.isEmpty();
        }

        void commit(AquaWaterPlugin plugin, Location origin, Map<Location, BlockData> saved) {
            if (isEmpty()) return;
            // One protection event for the whole impact, not one per material: a rolling deluge fires
            // this ~10 times already, and every listener on the server does real work per event.
            List<Block> all = new ArrayList<>(toAir.size() + toWater.size());
            all.addAll(toAir);
            all.addAll(toWater);
            Set<Block> allowed = new HashSet<>(Protect.filter(plugin, origin, all));
            if (allowed.isEmpty()) return;

            boolean ultraWarm = origin.getWorld() != null
                    && origin.getWorld().getEnvironment() == World.Environment.NETHER;

            for (Block b : toAir) {
                if (!allowed.contains(b)) continue;
                save(saved, b);
                if (appliedSample == null) appliedSample = b.getBlockData();
                b.setType(Material.AIR, false);
            }

            // Water is placed without physics in bulk — thousands of queued fluid updates per impact
            // is what used to stall the server. But physics is also how neighbours find out: without
            // it, torches keep burning underwater, redstone stays powered, sponges never absorb and
            // lava converts to stone at some random later moment. So the topmost layer of each column
            // — where all of that actually sits — does get an update. That is a few dozen blocks
            // instead of a few thousand.
            for (Block b : toWater) {
                if (!allowed.contains(b)) continue;
                save(saved, b);
                if (appliedSample == null) appliedSample = b.getBlockData();
                if (ultraWarm) {
                    // Vanilla does not allow standing water in the Nether. Placing it without physics
                    // skipped the fluid tick that would have flashed it to steam, which handed the
                    // plugin a way to flood the Nether permanently — and contradicted its own design,
                    // where the Nether is the attunement that makes water weakest.
                    b.setType(Material.AIR, false);
                    continue;
                }
                b.setType(Material.WATER, waterSurface.contains(b));
            }
            if (ultraWarm) steam(plugin, origin, 40);
        }
    }

    /** Impact in mid-air or on a floor (no surface face known). */
    public static void impact(AquaWaterPlugin plugin, Location at, double radius, int depth, double power) {
        impact(plugin, at, null, null, radius, depth, power);
    }

    /**
     * Full impact: detects whether a wall, ceiling or floor was struck and leaves matching aftermath.
     * Aftermath is permanent by default (config), so the consequences of attacks stay in the world.
     */
    public static void impact(AquaWaterPlugin plugin, Location hitPos, BlockFace face, Block hitBlock,
                              double radius, int depth, double power) {
        AquaConfig cfg = plugin.cfg();
        World w = hitPos.getWorld();
        if (w == null) return;

        double R = Math.min(Math.max(1.0, radius), cfg.maxBreakRadius);
        Edits edits = new Edits();

        boolean wall = face != null && face.getModY() == 0 && (face.getModX() != 0 || face.getModZ() != 0);
        boolean ceiling = face != null && face.getModY() < 0;

        if (cfg.breakBlocks && hitBlock != null && (wall || ceiling)) {
            carveWallPocket(w, hitBlock, face, R * 0.75, cfg, edits);
        }

        // Pool on the ground beneath the impact — its size/depth grows with the water amount.
        Location ground = Targeting.groundBelow(hitPos.clone().add(0, 0.6, 0), (int) Math.ceil(R) + 14);
        double poolR = Math.max(1.0, Math.min(R * (0.7 + 0.8 * power), cfg.maxBreakRadius));
        basin(w, ground, poolR, depth, power, cfg, edits);

        if (cfg.breakBlocks && hitBlock != null && (wall || ceiling)) {
            waterStreak(w, hitBlock, face, ground, cfg, edits);
        }

        Map<Location, BlockData> saved = cfg.permanentBreak || edits.isEmpty() ? null : new HashMap<>();
        edits.commit(plugin, hitPos, saved);

        // Only advertise debris we were actually allowed to create.
        blockBreakParticles(plugin, hitPos, edits.appliedSample, (int) (24 + 36 * power));
        scheduleRestore(plugin, saved);
    }

    /**
     * Massive crater for the nuke — carved in batches across ticks to avoid lag spikes.
     * The shape itself lives in {@link CraterShape}, which is where the reasoning (and its test) is.
     */
    public static void bigCrater(AquaWaterPlugin plugin, Location center, int radius, int maxDepth) {
        AquaConfig cfg = plugin.cfg();
        World w = center.getWorld();
        if (w == null) return;
        splash(plugin, center, 120);
        if (!cfg.breakBlocks) return;

        int R = Math.min(radius, 30);
        int cx = center.getBlockX(), cy = center.getBlockY() - 1, cz = center.getBlockZ();
        CraterShape shape = CraterShape.of(cx, cz, R, maxDepth, cfg.nukeBlastCount, cfg.nukeBlastRoughness);

        int rr = shape.reach();
        List<int[]> cols = new ArrayList<>();
        for (int dx = -rr; dx <= rr; dx++) {
            for (int dz = -rr; dz <= rr; dz++) cols.add(new int[]{dx, dz});
        }

        Map<Location, BlockData> saved = cfg.permanentBreak ? null : new HashMap<>();
        int batch = Math.max(60, cols.size() / 14);
        new BukkitRunnable() {
            int idx = 0;
            @Override
            public void run() {
                Edits edits = new Edits();
                int end = Math.min(cols.size(), idx + batch);
                for (; idx < end; idx++) {
                    int dx = cols.get(idx)[0], dz = cols.get(idx)[1];
                    double depth = shape.depthAt(dx, dz);
                    if (depth <= 0) continue;   // ragged rim teeth and surviving stumps

                    int d = Math.max(1, (int) Math.round(depth));
                    // Vaporise everything ABOVE the surface too (trees, buildings, terrain).
                    int up = Math.max(2, (int) Math.round(depth * 0.9
                            * (0.5 + 0.8 * edgeNoise01(cx + dx + 11, cz + dz + 5))));
                    for (int k = 1; k <= up; k++) {
                        edits.air(w.getBlockAt(cx + dx, cy + k, cz + dz), cfg);
                    }
                    for (int k = 0; k < d; k++) {
                        edits.water(w.getBlockAt(cx + dx, cy - k, cz + dz), cfg, k == 0);
                    }
                }
                edits.commit(plugin, center, saved);
                if (idx >= cols.size()) {
                    cancel();
                    scheduleRestore(plugin, saved);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Carve a bowl and fill its WHOLE depth with water. Depth scales with break-depth and power.
     *  The rim is deliberately ragged (not a perfect circle) and the column is cleared ABOVE ground too
     *  (vegetation, trees, walls), so the aftermath reads as a real blast — top and bottom both gone. */
    private static void basin(World w, Location ground, double poolR, int depth, double power,
                              AquaConfig cfg, Edits edits) {
        int gx = ground.getBlockX(), gz = ground.getBlockZ();
        int topAir = ground.getBlockY();
        int topSolid = topAir - 1;
        int ri = (int) Math.ceil(poolR * 1.2) + 2;
        int maxLayers = Math.max(1, Math.min(depth, 10));
        int aboveMax = Math.max(2, (int) Math.round(maxLayers * (0.6 + 0.7 * power)));
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double h = Math.sqrt(dx * dx + dz * dz);
                // Ragged edge: jitter the effective radius per-column so the rim isn't a clean circle.
                double edge = poolR + edgeNoise(gx + dx, gz + dz) * (0.8 + poolR * 0.16);
                if (h > edge) continue;
                double falloff = 1 - h / (edge + 0.001);
                if (cfg.breakBlocks) {
                    // 1) Strip everything above the surface (flowers, grass, trunks, low walls).
                    int up = (int) Math.round(aboveMax * falloff * (0.55 + 0.7 * edgeNoise01(gx + dx + 7, gz + dz - 3)));
                    for (int k = 0; k <= up; k++) {
                        edits.air(w.getBlockAt(gx + dx, topAir + k, gz + dz), cfg);
                    }
                    // 2) Carve the bowl below and fill it.
                    int d = Math.max(1, (int) Math.round(maxLayers * falloff * (0.5 + 0.5 * power)));
                    for (int k = 0; k < d; k++) {
                        Block b = w.getBlockAt(gx + dx, topSolid - k, gz + dz);
                        if (breakable(b, cfg)) {
                            edits.water(b, cfg, k == 0);   // k==0 is this column's surface
                        } else if (b.getType() != Material.WATER) {
                            break;
                        }
                    }
                } else {
                    // `break-blocks: false` used to still flood, because this branch bypassed the
                    // blacklist and the breakable check entirely. It now goes through the same gate,
                    // so switching breaking off really does mean "don't rearrange my world".
                    Block b = w.getBlockAt(gx + dx, topAir, gz + dz);
                    if (cfg.floodWithoutBreaking) edits.fill(b, cfg, true);
                }
            }
        }
    }

    /** Deterministic per-column noise in [-1, 1] — gives craters a ragged, organic rim. */
    private static double edgeNoise(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return ((h & 0xFFFF) / 65535.0) * 2.0 - 1.0;
    }

    /** Same hash mapped to [0, 1]. */
    private static double edgeNoise01(int x, int z) {
        return (edgeNoise(x, z) + 1.0) * 0.5;
    }

    /** Gouge a chunk out of a struck wall/ceiling block (into the surface). */
    private static void carveWallPocket(World w, Block hitBlock, BlockFace face, double r,
                                        AquaConfig cfg, Edits edits) {
        Vector n = face.getDirection();
        int cx = hitBlock.getX(), cy = hitBlock.getY(), cz = hitBlock.getZ();
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    double into = -(dx * n.getX() + dy * n.getY() + dz * n.getZ());
                    if (into < -0.5) continue; // skip blocks on the player's side of the surface
                    edits.air(w.getBlockAt(cx + dx, cy + dy, cz + dz), cfg);
                }
            }
        }
    }

    /** Run a column of water from a wall/ceiling impact down to the floor. */
    private static void waterStreak(World w, Block hitBlock, BlockFace face, Location ground,
                                    AquaConfig cfg, Edits edits) {
        Vector n = face.getDirection();
        int sx = hitBlock.getX() + (int) Math.round(n.getX());
        int sz = hitBlock.getZ() + (int) Math.round(n.getZ());
        int topY = hitBlock.getY();
        int botY = ground.getBlockY();
        boolean first = true;
        for (int y = topY; y >= botY; y--) {
            Block b = w.getBlockAt(sx, y, sz);
            if (b.isEmpty()) {
                edits.fill(b, cfg, first);
                first = false;
            } else if (b.getType().isSolid()) {
                break;
            }
        }
    }

    private static void save(Map<Location, BlockData> saved, Block b) {
        if (saved != null) saved.putIfAbsent(b.getLocation(), b.getBlockData());
    }

    private static void scheduleRestore(AquaWaterPlugin plugin, Map<Location, BlockData> saved) {
        if (saved == null || saved.isEmpty()) return; // permanent
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Map.Entry<Location, BlockData> en : saved.entrySet()) {
                try {
                    en.getKey().getBlock().setBlockData(en.getValue(), false);
                } catch (Exception ignored) {
                }
            }
        }, plugin.cfg().restoreTicks);
    }

    private static boolean breakable(Block b, AquaConfig cfg) {
        if (b.isEmpty()) return false;
        Material t = b.getType();
        if (t == Material.WATER || t == Material.LAVA) return false;
        if (cfg.blacklist.contains(t)) return false;
        return !(b.getState() instanceof Container);
    }

    // ---- Cosmetic -----------------------------------------------------------

    public static void blockBreakParticles(AquaWaterPlugin plugin, Location loc, BlockData sample, int count) {
        if (!plugin.cfg().particles || loc.getWorld() == null) return;
        if (sample == null) return;   // nothing broke -> no debris; don't fake it
        try {
            loc.getWorld().spawnParticle(Particle.BLOCK, loc, count, sample);
        } catch (Exception ignored) {
        }
    }

    public static void splash(AquaWaterPlugin plugin, Location loc, int count) {
        if (!plugin.cfg().particles || loc.getWorld() == null) return;
        World w = loc.getWorld();
        w.spawnParticle(Particle.SPLASH, loc, count, 0.5, 0.4, 0.5, 0.08);
        w.spawnParticle(Particle.FALLING_WATER, loc, Math.max(2, count / 2), 0.45, 0.45, 0.45, 0.02);
        w.spawnParticle(Particle.BUBBLE, loc, Math.max(2, count / 3), 0.4, 0.4, 0.4, 0.05);
    }

    /** A big, juicy water burst for major impacts. */
    public static void spray(AquaWaterPlugin plugin, Location loc, int count) {
        if (!plugin.cfg().particles || loc.getWorld() == null) return;
        World w = loc.getWorld();
        w.spawnParticle(Particle.SPLASH, loc, count, 1.1, 0.7, 1.1, 0.25);
        w.spawnParticle(Particle.FALLING_WATER, loc, count / 2, 0.9, 0.7, 0.9, 0.05);
        w.spawnParticle(Particle.BUBBLE_COLUMN_UP, loc, count / 3, 0.4, 0.3, 0.4, 0.12);
        w.spawnParticle(Particle.BUBBLE_POP, loc, count / 3, 0.8, 0.5, 0.8, 0.06);
    }

    public static void trail(AquaWaterPlugin plugin, Location loc) {
        if (!plugin.cfg().particles || loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(Particle.SPLASH, loc, 4, 0.18, 0.18, 0.18, 0.02);
        loc.getWorld().spawnParticle(Particle.BUBBLE, loc, 3, 0.12, 0.12, 0.12, 0.01);
        loc.getWorld().spawnParticle(Particle.DRIPPING_WATER, loc, 1, 0.1, 0.1, 0.1, 0.0);
    }

    public static void drip(AquaWaterPlugin plugin, Location loc) {
        if (!plugin.cfg().particles || loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(Particle.DRIPPING_WATER, loc, 2, 0.25, 0.1, 0.25, 0.0);
        loc.getWorld().spawnParticle(Particle.FALLING_WATER, loc, 1, 0.2, 0.05, 0.2, 0.0);
        loc.getWorld().spawnParticle(Particle.BUBBLE_POP, loc, 1, 0.2, 0.1, 0.2, 0.0);
    }

    /** Gentle ambient water shimmer (e.g. around the floating orb). */
    public static void aura(AquaWaterPlugin plugin, Location loc, double radius) {
        if (!plugin.cfg().particles || loc.getWorld() == null) return;
        World w = loc.getWorld();
        w.spawnParticle(Particle.BUBBLE, loc, 5, radius * 0.6, radius * 0.45, radius * 0.6, 0.01);
        w.spawnParticle(Particle.DRIPPING_WATER, loc, 2, radius * 0.55, radius * 0.3, radius * 0.55, 0.0);
        w.spawnParticle(Particle.SPLASH, loc, 2, radius * 0.5, radius * 0.3, radius * 0.5, 0.02);
    }

    /**
     * A translucent film over a cloud of drops. The drops themselves are discrete cubes with gaps
     * between them; a scatter of tinted dust in those gaps is what makes the mass read as one body
     * of water rather than a lattice of blocks.
     */
    public static void skin(AquaWaterPlugin plugin, Location center, double radius, int count) {
        if (!plugin.cfg().particles || center.getWorld() == null) return;
        World w = center.getWorld();
        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, count,
                radius * 0.62, radius * 0.62, radius * 0.62, 0.0,
                new Particle.DustTransition(DEEP, SHALLOW, (float) Math.max(1.0, radius * 0.75)));
        w.spawnParticle(Particle.DUST, center, Math.max(1, count / 3),
                radius * 0.5, radius * 0.5, radius * 0.5, 0.0,
                new Particle.DustOptions(FOAM, (float) Math.max(0.7, radius * 0.4)));
    }

    /** Flat expanding ring on the ground — reads as a pressure wave leaving an impact. */
    public static void shockwave(AquaWaterPlugin plugin, Location at, double radius, int points) {
        if (!plugin.cfg().particles || at.getWorld() == null) return;
        World w = at.getWorld();
        for (int i = 0; i < points; i++) {
            double a = (2 * Math.PI * i) / points;
            Location p = at.clone().add(Math.cos(a) * radius, 0.12, Math.sin(a) * radius);
            w.spawnParticle(Particle.SPLASH, p, 2, 0.05, 0.02, 0.05, 0.02);
            if (i % 3 == 0) {
                w.spawnParticle(Particle.DUST, p, 1, 0.05, 0.02, 0.05, 0.0,
                        new Particle.DustOptions(FOAM, 1.2f));
            }
        }
    }

    /** Steam — water losing the fight against heat (dry biomes, the Nether, lava). */
    public static void steam(AquaWaterPlugin plugin, Location at, int count) {
        if (!plugin.cfg().particles || at.getWorld() == null) return;
        at.getWorld().spawnParticle(Particle.WHITE_SMOKE, at, count, 0.25, 0.3, 0.25, 0.02);
    }

    /** Drifting strands of water pulled toward a point (healing, gathering, condensation). */
    public static void stream(AquaWaterPlugin plugin, Location from, Location to, int count, Color tint) {
        if (!plugin.cfg().particles || from.getWorld() == null) return;
        try {
            from.getWorld().spawnParticle(Particle.TRAIL, from, count, 0.35, 0.35, 0.35, 0.0,
                    new Particle.Trail(to, tint, 12));
        } catch (Throwable t) {
            from.getWorld().spawnParticle(Particle.FALLING_WATER, from, count, 0.3, 0.3, 0.3, 0.0);
        }
    }

    public static Color shallow() { return SHALLOW; }
    public static Color foam() { return FOAM; }

    /**
     * Play a sound, scaled by the master volume. The scalar exists because this is a plugin used for
     * recording: a shotgun lands up to nine impacts at once, and eighteen stacked sounds bury a
     * voice track. Note that in Minecraft {@code volume > 1.0} extends audible range rather than
     * loudness, so the knob mostly controls how far the noise carries.
     */
    public static void sound(AquaWaterPlugin plugin, Location loc, String key, float vol, float pitch) {
        AquaConfig cfg = plugin.cfg();
        if (!cfg.sounds || cfg.soundVolume <= 0.0 || loc.getWorld() == null) return;
        try {
            loc.getWorld().playSound(loc, key, (float) (vol * cfg.soundVolume), pitch);
        } catch (Exception ignored) {
        }
    }
}
