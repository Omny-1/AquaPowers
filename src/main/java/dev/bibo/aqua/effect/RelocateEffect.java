package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.orb.WaterCollector;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.Protect;
import dev.bibo.aqua.util.Targeting;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Water Summoning" — pulls nearby water to where you aim (or onto the entity you target),
 * dealing damage on arrival. The delivered water flows naturally.
 *
 * <p>This used to destroy roughly four fifths of the water it claimed to move. The delivery loop
 * walked a list of up to 120 offsets inside a radius-1.8 ball — about 24 distinct block cells — and
 * counted <i>duplicates</i> toward its "placed enough" condition, so it took 120 blocks out of the
 * world and put ~24 back. Aiming at a wall made it worse. Placement is now deduplicated by position
 * and the leftovers go home through the borrowed-state contract.
 */
public final class RelocateEffect extends BaseEffect {

    private final int taken;
    private final List<Location> starts = new ArrayList<>();
    private final List<Vector> offsets;
    private final LivingEntity target;
    private final double damage;
    private final int travel = 22;
    private Location dest;
    private boolean landed = false;

    public RelocateEffect(AquaWaterPlugin plugin, Player player, int radius, int cap, double damage) {
        super(plugin, player, new ArrayList<>());
        this.damage = damage;
        this.target = Targeting.targetEntity(player, 30);
        this.dest = target != null
                ? target.getLocation().add(0, target.getHeight() * 0.5, 0)
                : Targeting.lookPoint(player, 30);

        List<Block> wanted = new ArrayList<>();
        for (WaterCollector.Drained d : WaterCollector.findSources(player, radius, cap)) {
            wanted.add(d.loc().getBlock());
        }
        // Honour `collect.drain-source`: with it off the water is only read, never removed.
        this.taken = cfg.drainSource ? borrowAll(player.getLocation(), wanted, Material.AIR) : 0;
        float scale = cfg.orbScale;
        for (Block b : wanted) {
            Location at = b.getLocation().add(0.5, 0.5, 0.5);
            starts.add(at);
            spawn(at, scale);
        }
        // With little ground water, materialise a token amount at the caster so the throw is visible.
        if (blocks.size() < 6) {
            Location c = player.getLocation();
            int need = 14 - blocks.size();
            for (int i = 0; i < need; i++) {
                Location at = c.clone().add((Math.random() * 2 - 1) * 1.5,
                        0.8 + Math.random() * 1.5, (Math.random() * 2 - 1) * 1.5);
                starts.add(at);
                spawn(at, scale);
            }
        }
        this.offsets = Geometry.ball(Math.max(1, blocks.size()), 1.8);
        WorldFx.sound(plugin, player.getLocation(), "minecraft:item.bucket.fill", 1.0f, 1.1f);
    }

    @Override
    public boolean tick() {
        if (!casterValid()) return true;
        age++;
        if (target != null && !target.isDead() && target.isValid()) {
            dest = target.getLocation().add(0, target.getHeight() * 0.5, 0); // home onto the entity
        }
        double t = Geometry.easeOut(Math.min(1.0, age / (double) travel));
        for (int i = 0; i < blocks.size(); i++) {
            Location p = Geometry.lerp(starts.get(i), dest.clone().add(offsets.get(i % offsets.size())), t);
            p.add(0, Math.sin(t * Math.PI) * 0.6, 0);
            blocks.get(i).teleport(p);
            if (age % 6 == 0) WorldFx.trail(plugin, p);
        }

        if (age >= travel && !landed) {
            landed = true;
            WorldFx.damage(plugin, dest, Math.max(2.5, 1.0 + Math.sqrt(taken) * 0.35),
                    damage, player, 0.35, 0.4);

            // Distinct cells only, so "how many did we place" means what it says.
            Set<Block> cells = new LinkedHashSet<>();
            for (Vector off : offsets) {
                if (cells.size() >= taken) break;
                try {
                    Block b = dest.clone().add(off).getBlock();
                    if (b.isEmpty()) cells.add(b);
                } catch (Exception ignored) {
                }
            }
            int placed = 0;
            for (Block b : Protect.filter(plugin, dest, new ArrayList<>(cells))) {
                b.setType(Material.WATER, true); // settles and flows
                placed++;
            }
            // Only the delivered share is spent; whatever would not fit at the destination goes back
            // where it came from. Returning all of it would duplicate water, returning none of it
            // would destroy it — which is exactly what used to happen.
            spendBorrowed(placed);

            WorldFx.splash(plugin, dest, 30);
            WorldFx.sound(plugin, dest, "minecraft:entity.player.splash.high_speed", 1.0f, 1.0f);
            return true;
        }
        return false;
    }
}
