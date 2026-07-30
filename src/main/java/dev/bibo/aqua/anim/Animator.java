package dev.bibo.aqua.anim;

import dev.bibo.aqua.AquaWaterPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central per-tick scheduler that drives every active {@link TickEffect}.
 *
 * <p>Also the plugin's clock and its budget. Both exist because of the same realisation: the cost of
 * this plugin is not CPU on the main thread, it is <b>display entities being broadcast to every
 * nearby client</b>. Five benders inside one tracking radius is not five packet streams, it is
 * twenty-five, and none of it shows up in a TPS graph or a profiler — it surfaces as rubber-banding
 * while the server looks perfectly healthy. So the entity count needs a hard ceiling, and refusing a
 * cast out loud is better than quietly grinding the network into the ground.
 *
 * <p>Everything here runs on the main thread; see {@link TickEffect}.
 */
public final class Animator {

    private final AquaWaterPlugin plugin;
    private final List<TickEffect> active = new ArrayList<>();
    private final List<TickEffect> pending = new ArrayList<>();
    private BukkitTask task;

    /**
     * Monotonic tick counter, and the plugin's only source of "how long ago".
     * {@code System.currentTimeMillis()} is a wall clock: an NTP correction backwards makes elapsed
     * time negative and jams every cooldown, and it keeps running while the server is off — which
     * would hand out a free ultimate after every overnight restart once cooldowns are persisted.
     */
    private long ticks;

    public Animator(AquaWaterPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::run, 1L, 1L);
    }

    /** Ticks since the plugin was enabled. Never goes backwards. */
    public long ticks() {
        return ticks;
    }

    /** Is there room for another effect of roughly this many display entities? */
    public boolean hasRoom(UUID owner, int displays) {
        if (active.size() + pending.size() >= plugin.cfg().maxEffects) return false;
        if (totalDisplays() + displays > plugin.cfg().maxDisplays) return false;
        if (owner != null && countFor(owner) >= plugin.cfg().maxEffectsPerPlayer) return false;
        return true;
    }

    /** Register a new effect. Safe to call from within another effect's tick. */
    public void add(TickEffect effect) {
        if (effect != null) pending.add(effect);
    }

    public int activeCount() {
        return active.size() + pending.size();
    }

    public int totalDisplays() {
        int n = 0;
        for (TickEffect e : active) n += e.displayCount();
        for (TickEffect e : pending) n += e.displayCount();
        return n;
    }

    public int countFor(UUID owner) {
        int n = 0;
        for (TickEffect e : active) if (owner.equals(e.owner())) n++;
        for (TickEffect e : pending) if (owner.equals(e.owner())) n++;
        return n;
    }

    /** Effect counts by class name, for {@code /aqua debug}. */
    public Map<String, Integer> breakdown() {
        Map<String, Integer> out = new HashMap<>();
        for (TickEffect e : active) out.merge(e.label(), 1, Integer::sum);
        for (TickEffect e : pending) out.merge(e.label(), 1, Integer::sum);
        return out;
    }

    /** Drop everything a player owns (they left, or lost their powers). */
    public int dropOwnedBy(UUID owner) {
        int removed = 0;
        Iterator<TickEffect> it = active.iterator();
        while (it.hasNext()) {
            TickEffect e = it.next();
            if (owner.equals(e.owner())) {
                safeCleanup(e);
                it.remove();
                removed++;
            }
        }
        pending.removeIf(e -> {
            if (owner.equals(e.owner())) {
                safeCleanup(e);
                return true;
            }
            return false;
        });
        return removed;
    }

    private void run() {
        ticks++;
        if (!pending.isEmpty()) {
            active.addAll(pending);
            pending.clear();
        }
        Iterator<TickEffect> it = active.iterator();
        while (it.hasNext()) {
            TickEffect e = it.next();
            boolean done;
            try {
                done = e.tick();
            } catch (Throwable t) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Water effect crashed", t);
                done = true;
            }
            if (done) {
                safeCleanup(e);
                it.remove();
            }
        }
    }

    public void stopAll() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (TickEffect e : active) safeCleanup(e);
        for (TickEffect e : pending) safeCleanup(e);
        active.clear();
        pending.clear();
    }

    private void safeCleanup(TickEffect e) {
        try {
            e.cleanup();
        } catch (Throwable t) {
            plugin.getLogger().warning("Water effect cleanup failed: " + t);
        }
    }
}
