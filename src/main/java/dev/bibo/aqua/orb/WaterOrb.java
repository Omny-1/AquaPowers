package dev.bibo.aqua.orb;

import dev.bibo.aqua.AquaConfig;
import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.anim.TickEffect;
import dev.bibo.aqua.env.Attunement;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The floating body of water above a player's head. Its size scales with the amount of water.
 * Phase 1: water rises from its sources and gathers.
 * Phase 2: it idles (slow rotation + bob, dripping) until consumed by a form or dispersed.
 *
 * <p>Its <b>silhouette announces the caster's state</b> from across the map: a sphere normally, a
 * crossed twin-ring gyroscope in Awakening. And in dry or burning country it visibly boils away,
 * so standing in the Nether with a full orb is something you can see happening to you.
 */
public final class WaterOrb implements TickEffect {

    private final AquaWaterPlugin plugin;
    private final AquaConfig cfg;
    private final Player player;
    private final World world;
    private final List<WaterBlock> blocks = new ArrayList<>();
    private List<Vector> offsets;
    private final List<Location> sources = new ArrayList<>();
    /**
     * Parallel to {@link #blocks}: where each drop came from in the world, or {@code null} if it was
     * condensed out of rain rather than taken from a block. Kept per-drop rather than as a separate
     * list because drops are removed from the tail (evaporation, partial spend) and topping the orb
     * up interleaves real and conjured water — with two independent lists, dropping a rain drop would
     * silently discard a real block's restore entry and leak that water out of the world.
     */
    private final List<WaterCollector.Drained> origin = new ArrayList<>();

    private final int duration;
    private double radius;
    private final float scale;
    private int age = 0;
    private double spin = 0;
    private boolean consumed = false;
    private boolean taken = false;
    private boolean shapedAwakened = false;
    private double evaporation = 0;

    public WaterOrb(AquaWaterPlugin plugin, Player player, List<WaterCollector.Drained> drained,
                    List<Location> conjured, int duration) {
        this.plugin = plugin;
        this.cfg = plugin.cfg();
        this.player = player;
        this.world = player.getWorld();
        this.duration = Math.max(4, duration);

        int total = drained.size() + (conjured == null ? 0 : conjured.size());
        double p = Math.max(0.12, Math.min(1.0, total / (double) Math.max(1, cfg.maxBlocks)));
        this.radius = cfg.orbRadius * (0.6 + 0.9 * p);
        this.scale = (float) (cfg.orbScale * (0.8 + 0.6 * p));

        for (WaterCollector.Drained d : drained) addDrop(d.loc().clone().add(0.5, 0.5, 0.5), d);
        if (conjured != null) for (Location at : conjured) addDrop(at, null);
        reshape();

        WorldFx.sound(plugin, player.getLocation(), "minecraft:item.bucket.fill", 1.0f, 0.7f);
    }

    private void addDrop(Location at, WaterCollector.Drained from) {
        sources.add(at);
        origin.add(from);
        blocks.add(new WaterBlock(world, at, plugin.style(), scale,
                cfg.teleportDuration, cfg.brightness, cfg.viewRange, cfg.orbGlow));
    }

    /** Remove the newest drop, keeping every parallel list in step. */
    private WaterBlock dropLast() {
        int last = blocks.size() - 1;
        if (last < 0) return null;
        WaterBlock b = blocks.remove(last);
        sources.remove(last);
        origin.remove(last);
        b.remove();
        return b;
    }

    /** Recompute the layout for the current volume and the caster's current state. */
    private void reshape() {
        double p = Math.max(0.12, Math.min(1.0, blocks.size() / (double) Math.max(1, cfg.maxBlocks)));
        this.radius = cfg.orbRadius * (0.6 + 0.9 * p);
        this.shapedAwakened = isAwakened();
        this.offsets = shapedAwakened
                ? Geometry.gyroscope(blocks.size(), radius * 1.15)
                : Geometry.sphere(blocks.size(), radius);
    }

    private boolean isAwakened() {
        return plugin.users().user(player).isAwakening();
    }

    public Location center() {
        // Lift the body so its bottom always clears the head, even when the orb is large.
        return player.getLocation().add(0, player.getHeight() + radius + cfg.heightAboveHead, 0);
    }

    public boolean isReady() {
        return !consumed && age > duration;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public int blockCount() {
        return blocks.size();
    }

    public boolean isFull() {
        return blocks.size() >= cfg.maxBlocks;
    }

    /** Top up an existing orb with more water (second RMB collects only the missing amount). */
    public void topUp(List<WaterCollector.Drained> more, List<Location> conjured) {
        if (taken || consumed) return;
        int room = cfg.maxBlocks - blocks.size();
        int added = 0;
        if (more != null) {
            for (WaterCollector.Drained d : more) {
                if (added >= room) break;
                addDrop(d.loc().clone().add(0.5, 0.5, 0.5), d);
                added++;
            }
        }
        if (conjured != null) {
            for (Location at : conjured) {
                if (added >= room) break;
                addDrop(at, null);
                added++;
            }
        }
        if (added == 0) return;
        reshape();
        WorldFx.sound(plugin, center(), "minecraft:item.bucket.fill", 0.8f, 1.3f);
    }

    /** Spend only part of the orb (e.g. a dash sips a small share), keeping the rest floating. */
    public void consumePartial(int count) {
        if (taken || consumed || count <= 0) return;
        int n = Math.min(count, blocks.size());
        for (int i = 0; i < n; i++) dropLast();
        if (blocks.isEmpty()) {
            consumed = true;
            return;
        }
        reshape();
        WorldFx.splash(plugin, center(), 10);
    }

    /** Hand the live water blocks to a form. The orb stops idling; caller now owns the blocks. */
    public List<WaterBlock> takeBlocks() {
        taken = true;
        consumed = true;
        return blocks;
    }

    /** Develop the body back into nothing (optionally returning the water to the world). */
    public void disperse() {
        if (taken) return;
        consumed = true;
        WorldFx.splash(plugin, center(), 30);
        WorldFx.sound(plugin, center(), "minecraft:entity.generic.splash", 1.0f, 1.2f);
    }

    @Override
    public boolean tick() {
        if (consumed) return true;
        if (!player.isOnline() || player.isDead() || player.getWorld() != world) {
            consumed = true;
            return true;
        }

        age++;
        spin += cfg.spinSpeed;
        Location c = center();

        if (age <= duration) {
            double e = Geometry.easeOut(age / (double) duration);
            for (int i = 0; i < blocks.size(); i++) {
                Vector off = Geometry.rotateY(offsets.get(i), spin);
                Location target = c.clone().add(off);
                Location from = sources.get(i);
                Location pos = Geometry.lerp(from, target, e);
                pos.add(0, Math.sin(e * Math.PI) * 0.25, 0);
                // Rising water streaks: each drop is drawn out along the path it is travelling,
                // so the gather reads as strands of water pulled up rather than cubes sliding.
                Vector travel = target.toVector().subtract(from.toVector());
                if (travel.lengthSquared() > 0.25) {
                    blocks.get(i).stretch(travel, (float) (2.4 - 1.4 * e), (float) (0.65 + 0.35 * e));
                }
                blocks.get(i).teleport(pos);
            }
            if (age % 4 == 0) {
                for (int i = 0; i < sources.size(); i += Math.max(1, sources.size() / 6)) {
                    WorldFx.trail(plugin, sources.get(i));
                    WorldFx.stream(plugin, sources.get(i), c, 2, WorldFx.shallow());
                }
            }
            if (age == duration) {
                for (WaterBlock b : blocks) b.relax();
                WorldFx.sound(plugin, c, "minecraft:block.bubble_column.upwards_inside", 1.0f, 1.3f);
                WorldFx.skin(plugin, c, radius, 24);
            }
        } else {
            if (shapedAwakened != isAwakened()) {
                reshape();
                WorldFx.sound(plugin, c, "minecraft:block.beacon.power_select", 0.8f, 1.8f);
                WorldFx.skin(plugin, c, radius, 30);
            }
            evaporate(c);
            if (consumed) return true;
            // Follow the ambient light as the player moves between sun, cave and storm.
            if (age % 20 == 0 && cfg.brightness < 0) {
                for (WaterBlock b : blocks) b.refreshLight(c, cfg.brightness);
            }

            // The drops interpolate over `teleportDuration` ticks client-side, so re-sending them
            // every tick is pure packet waste: one move per interpolation window looks identical.
            int stride = Math.max(1, cfg.teleportDuration);
            if (age % stride == 0) {
                double bob = Math.sin(age * 0.12) * 0.07;
                boolean gyro = shapedAwakened;
                for (int i = 0; i < blocks.size(); i++) {
                    // In Awakening the two rings counter-rotate against the core, so the shape churns.
                    double localSpin = gyro && i >= blocks.size() / 5 ? spin * (i % 2 == 0 ? 2.1 : -1.6) : spin;
                    Vector off = Geometry.rotateY(offsets.get(i), localSpin);
                    blocks.get(i).teleport(c.clone().add(off.getX(), off.getY() + bob, off.getZ()));
                }
            }
            // Constant gentle dripping + ambient shimmer around the orb.
            if (age % 6 == 0 && !blocks.isEmpty()) {
                WorldFx.drip(plugin, blocks.get((age / 6) % blocks.size()).location());
                WorldFx.drip(plugin, c.clone().add(0, -0.4, 0));
            }
            if (age % 4 == 0) {
                WorldFx.aura(plugin, c, radius);
                WorldFx.skin(plugin, c, radius, 4);
            }
        }
        return false;
    }

    /** Hot, dry country boils the orb away a drop at a time — visibly, with steam. */
    private void evaporate(Location c) {
        Attunement att = plugin.users().user(player).getAttunement();
        if (att.evaporatePerSec <= 0 || blocks.isEmpty()) return;
        evaporation += att.evaporatePerSec / 20.0;
        while (evaporation >= 1.0 && !blocks.isEmpty()) {
            evaporation -= 1.0;
            Location lost = blocks.get(blocks.size() - 1).location();
            WorldFx.steam(plugin, lost, 6);
            dropLast();
        }
        if (blocks.isEmpty()) {
            consumed = true;
            WorldFx.sound(plugin, c, "minecraft:block.fire.extinguish", 1.0f, 1.2f);
            WorldFx.steam(plugin, c, 30);
            return;
        }
        if (age % 20 == 0) {
            reshape();
            WorldFx.steam(plugin, c, 4);
        }
    }

    @Override
    public java.util.UUID owner() {
        return player.getUniqueId();
    }

    @Override
    public int displayCount() {
        return blocks.size();
    }

    @Override
    public String label() {
        return "WaterOrb";
    }

    @Override
    public void cleanup() {
        if (taken) return; // a form owns the blocks now
        for (WaterBlock b : blocks) b.remove();
        if (cfg.drainSource && cfg.restoreSourceOnDisperse) {
            List<WaterCollector.Drained> real = new ArrayList<>(origin.size());
            for (WaterCollector.Drained d : origin) {
                if (d != null) real.add(d);   // rain-conjured drops have nowhere to go back to
            }
            WaterCollector.restore(real);
        }
    }
}
