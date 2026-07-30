package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.Map;

/** "Part the Waters" (Eneru-style): parts the REAL water into two walls along a long corridor ahead of you,
 *  keeping the corridor dry, then lets the water flow back in. No illusory display blocks — the actual
 *  water in the world is simply pushed aside. Limited to a few simultaneous casts per player. */
public final class PartWaterEffect extends BaseEffect {

    /** How many parting corridors a single player may hold open at once. */
    public static final int MAX_ACTIVE = 3;
    /** Upper bound on blocks one corridor may hold aside (memory + restore cost). */
    private static final int CAP = 900;
    /** Main-thread only, like everything here; a plain map is the honest choice. */
    private static final Map<UUID, AtomicInteger> ACTIVE = new HashMap<>();

    public static int activeFor(UUID id) {
        AtomicInteger a = ACTIVE.get(id);
        return a == null ? 0 : a.get();
    }

    private final Vector dir;
    private final Vector perp;
    private final Location origin;
    private final int length;
    private final int halfWidth;
    private final int upHeight;
    private final int downDepth;
    private final int duration;
    private final UUID ownerId;
    private boolean counted = false;

    public PartWaterEffect(AquaWaterPlugin plugin, Player player, int length, int halfWidth,
                           int upHeight, int downDepth, int duration) {
        super(plugin, player, new ArrayList<>());
        this.ownerId = player.getUniqueId();
        this.length = length;
        this.halfWidth = halfWidth;
        this.upHeight = upHeight;
        this.downDepth = downDepth;
        this.duration = duration;
        Vector d = player.getEyeLocation().getDirection();
        d.setY(0);
        if (d.lengthSquared() < 1e-6) d = new Vector(1, 0, 0);
        this.dir = d.normalize();
        this.perp = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        this.origin = player.getLocation().clone();

        clearCorridor();

        if (borrowedCount() > 0) {
            ACTIVE.computeIfAbsent(ownerId, k -> new AtomicInteger()).incrementAndGet();
            counted = true;
            WorldFx.sound(plugin, origin, "minecraft:item.bucket.empty", 1.2f, 0.5f);
        }
    }

    /** Did the corridor actually open? If not, the caster should not be charged for it. */
    public boolean opened() {
        return counted;
    }

    @Override
    public boolean tick() {
        if (borrowedCount() == 0) return true;   // there was no water to part
        if (!casterValid() && age > 0) {
            // Fine to leave the corridor to finish on its own, but not to keep re-clearing it for a
            // caster who is no longer here.
            return true;
        }
        age++;
        // Keep the corridor dry while it holds (water tries to flow back in).
        if (age % 20 == 0) clearCorridor();
        // A few particles along the parted walls so the effect reads clearly.
        if (age % 6 == 0) {
            for (int t = 4; t <= length; t += 8) {
                Location c = origin.clone().add(dir.clone().multiply(t)).add(0, 1, 0);
                WorldFx.trail(plugin, c.clone().add(perp.clone().multiply(halfWidth + 0.5)));
                WorldFx.trail(plugin, c.clone().add(perp.clone().multiply(-(halfWidth + 0.5))));
            }
        }
        return age > duration;
    }

    /**
     * Open (or re-open) the corridor.
     *
     * <p>The re-clear runs fifteen times over a cast, and each pass used to hand up to 900 blocks to
     * the protection gate — around 13 500 permission checks per cast, times three concurrent
     * corridors. Permission is now asked once, on the first pass, and the allowed set is reused;
     * later passes only touch positions we already own.
     */
    private void clearCorridor() {
        if (age == 0) {
            List<Block> wanted = new ArrayList<>();
            outer:
            for (int t = 1; t <= length; t++) {
                Location centre = origin.clone().add(dir.clone().multiply(t));
                for (int hw = -halfWidth; hw <= halfWidth; hw++) {
                    for (int h = -downDepth; h <= upHeight; h++) {
                        if (wanted.size() >= CAP) break outer;
                        Block b = centre.clone().add(perp.clone().multiply(hw)).add(0, h, 0).getBlock();
                        if (b.getType() == Material.WATER) wanted.add(b);
                    }
                }
            }
            borrowAll(origin, wanted, Material.AIR);
            return;
        }
        // Subsequent passes: only re-dry what we already have permission for.
        for (Location loc : borrowedPositions()) {
            Block b = loc.getBlock();
            if (b.getType() == Material.WATER) b.setType(Material.AIR, false);
        }
    }

    @Override
    public void cleanup() {
        if (counted) {
            AtomicInteger a = ACTIVE.get(ownerId);
            if (a != null && a.decrementAndGet() <= 0) ACTIVE.remove(ownerId);
        }
        super.cleanup();   // batched restore, with physics, only where our hole is still a hole
    }
}
