package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaConfig;
import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.anim.TickEffect;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Protect;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared plumbing <i>and obligations</i> for water-form effects.
 *
 * <p>This class used to be a bag of conveniences. That was the root cause of seven separate bugs
 * across six files, because it handed subclasses tools but never asked them the four questions every
 * effect has to answer:
 *
 * <ul>
 *   <li><b>Who owns me?</b> — {@link #owner()}, so the animator can budget and report.</li>
 *   <li><b>Is my context still real?</b> — {@link #casterValid()}. Only the orb used to check for a
 *       world change; nine other effects happily kept rendering across worlds.</li>
 *   <li><b>What did I take from the world?</b> — {@link #borrow}. Two effects drained hundreds of
 *       water blocks and simply forgot to give them back, which was a free world-eraser.</li>
 *   <li><b>How loud am I allowed to be?</b> — {@link #announceHit}, so per-victim impact FX can't
 *       fire twenty sounds from one point in one tick.</li>
 * </ul>
 *
 * Forgetting any of them is now either impossible or visible at the call site.
 */
public abstract class BaseEffect implements TickEffect {

    /** A block this effect took from the world, and what has to go back there. */
    private record Borrowed(BlockData saved, Material placed) {}

    protected final AquaWaterPlugin plugin;
    protected final AquaConfig cfg;
    protected final Player player;
    protected final World homeWorld;
    protected final List<WaterBlock> blocks;
    protected int age = 0;

    /** Blocks taken from the world, keyed by position so re-taking the same spot can't double-count. */
    private final Map<Location, Borrowed> borrowed = new LinkedHashMap<>();
    private boolean keepChanges = false;
    private int lastHitFxAge = -100;

    protected BaseEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks) {
        this.plugin = plugin;
        this.cfg = plugin.cfg();
        this.player = player;
        this.homeWorld = player.getWorld();
        this.blocks = blocks;
    }

    @Override
    public UUID owner() {
        return player.getUniqueId();
    }

    @Override
    public int displayCount() {
        return blocks.size();
    }

    /**
     * Is the caster still someone this effect can meaningfully belong to? Effects anchored to a
     * player must stop when the answer is no — otherwise they render into the wrong world and, in
     * the surf's case, start subtracting coordinates from two different ones.
     */
    protected boolean casterValid() {
        return player.isOnline() && !player.isDead() && player.getWorld().equals(homeWorld);
    }

    // ---- borrowed world state ----------------------------------------------

    /**
     * Take a block out of the world, remembering how to put it back. Returns false if the server's
     * protection plugins refused, in which case nothing was touched.
     */
    protected boolean borrow(Block b, Material replaceWith) {
        if (b == null) return false;
        Location key = b.getLocation();
        if (borrowed.containsKey(key)) return true;
        if (Protect.filter(plugin, key, new ArrayList<>(List.of(b))).isEmpty()) return false;
        borrowed.put(key, new Borrowed(b.getBlockData().clone(), replaceWith));
        b.setType(replaceWith, false);
        return true;
    }

    /** Bulk version: one protection query for the whole batch instead of one per block. */
    protected int borrowAll(Location origin, List<Block> wanted, Material replaceWith) {
        if (wanted.isEmpty()) return 0;
        int taken = 0;
        for (Block b : Protect.filter(plugin, origin, wanted)) {
            Location key = b.getLocation();
            if (borrowed.containsKey(key)) continue;
            borrowed.put(key, new Borrowed(b.getBlockData().clone(), replaceWith));
            b.setType(replaceWith, false);
            taken++;
        }
        return taken;
    }

    protected int borrowedCount() {
        return borrowed.size();
    }

    /** Positions we already hold, for effects that need to keep re-asserting their change. */
    protected List<Location> borrowedPositions() {
        return new ArrayList<>(borrowed.keySet());
    }

    /**
     * Declare that this effect's world changes are permanent — a nuclear crater is supposed to stay.
     * Deliberately opt-in: "took it and lost it" should never be what happens by default.
     */
    protected void keepChanges() {
        this.keepChanges = true;
    }

    /**
     * Mark {@code n} borrowed blocks as genuinely spent, so they are not handed back.
     *
     * <p>Needed by anything that moves water somewhere else and may not fit all of it: the delivered
     * share is consumed, the remainder must go home. Returning everything would duplicate the water;
     * returning nothing would destroy it.
     */
    protected void spendBorrowed(int n) {
        if (n <= 0) return;
        Iterator<Location> it = borrowed.keySet().iterator();
        for (int i = 0; i < n && it.hasNext(); i++) {
            it.next();
            it.remove();
        }
    }

    /**
     * Put everything back, in batches, and only where our own block is still standing.
     *
     * <p>Two things were wrong before. Restores wrote the saved state back unconditionally, so a
     * player who built on a temporary ice bridge lost their block when the timer fired. And one
     * effect restored up to 900 blocks with physics in a single tick — the exact fluid-update storm
     * that had already been removed from the crater code.
     */
    private void returnBorrowed() {
        if (borrowed.isEmpty()) return;
        List<Map.Entry<Location, Borrowed>> pending = new ArrayList<>(borrowed.entrySet());
        borrowed.clear();
        final int perTick = Math.max(16, cfg.restoreBatch);
        new org.bukkit.scheduler.BukkitRunnable() {
            int idx = 0;
            @Override
            public void run() {
                int end = Math.min(pending.size(), idx + perTick);
                for (; idx < end; idx++) {
                    Map.Entry<Location, Borrowed> e = pending.get(idx);
                    try {
                        Block b = e.getKey().getBlock();
                        // Someone may have built here since. Their block wins over our bookkeeping.
                        if (b.getType() != e.getValue().placed()) continue;
                        b.setBlockData(e.getValue().saved(), true);
                    } catch (Exception ignored) {
                    }
                }
                if (idx >= pending.size()) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ---- rendering helpers --------------------------------------------------

    protected void place(int i, Location loc) {
        if (i >= 0 && i < blocks.size()) blocks.get(i).teleport(loc);
    }

    /** Snapshot the blocks' current positions (e.g. to lerp from the orb shape). */
    protected List<Location> snapshot() {
        List<Location> out = new ArrayList<>(blocks.size());
        for (WaterBlock b : blocks) out.add(b.location());
        return out;
    }

    protected List<LivingEntity> nearbyLiving(Location at, double radius, Set<java.util.UUID> exclude) {
        List<LivingEntity> out = new ArrayList<>();
        if (at.getWorld() == null) return out;
        for (Entity e : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
            if (!WorldFx.targetable(cfg, e, player)) continue;
            if (exclude != null && exclude.contains(e.getUniqueId())) continue;
            if (at.distance(e.getLocation()) <= radius) out.add((LivingEntity) e);
        }
        return out;
    }

    /**
     * Gate for impact FX fired from inside a per-victim loop. A whip catching ten mobs used to play
     * twenty sounds from one point in one tick, and a shotgun eighteen — a wall of noise and a burst
     * of packets for no extra information.
     *
     * @return true at most once every few ticks; call sites should skip their FX otherwise.
     */
    protected boolean announceHit() {
        if (age - lastHitFxAge < 4) return false;
        lastHitFxAge = age;
        return true;
    }

    protected void splash(Location at, int n) {
        WorldFx.splash(plugin, at, n);
    }

    /** Spawn a fresh water block and track it for cleanup (used by water-creating abilities). */
    protected WaterBlock spawn(Location at, float scale) {
        WaterBlock b = new WaterBlock(at.getWorld(), at, plugin.style(), scale,
                cfg.teleportDuration, cfg.brightness, cfg.viewRange, cfg.orbGlow);
        blocks.add(b);
        return b;
    }

    /** Draw every drop out along a shared direction — jets, lashes, sheets in motion. */
    protected void stretchAll(Vector dir, float along, float across) {
        for (WaterBlock b : blocks) b.stretch(dir, along, across);
    }

    @Override
    public void cleanup() {
        for (WaterBlock b : blocks) b.remove();
        if (!keepChanges) returnBorrowed();
        else borrowed.clear();
    }
}
