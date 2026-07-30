package dev.bibo.aqua.anim;

import java.util.UUID;

/**
 * A unit of animation driven once per server tick by the {@link Animator}.
 *
 * <p><b>Threading contract:</b> every method here — and every class in this plugin — runs on the
 * server's main thread only. Nothing is safe to call from another thread, because the Bukkit API it
 * touches isn't either. Any concurrent collection you find in this codebase is there for iteration
 * safety, not for cross-thread access.
 */
public interface TickEffect {

    /**
     * Advance one tick.
     *
     * @return true when the effect is finished (the Animator will then call {@link #cleanup()}).
     */
    boolean tick();

    /** Release any resources (despawn display entities, return borrowed blocks). Called exactly once. */
    void cleanup();

    /**
     * Who cast this, or {@code null} for effects with no caster.
     *
     * <p>Without this the animator cannot enforce a per-player budget, cannot report who is holding
     * the display entities, and cannot drop a leaver's effects — three separate problems that all
     * traced back to this interface not knowing this one thing.
     */
    default UUID owner() {
        return null;
    }

    /** Roughly how many display entities this effect holds, for budgeting and diagnostics. */
    default int displayCount() {
        return 0;
    }

    /** Short label for {@code /aqua debug}. */
    default String label() {
        return getClass().getSimpleName();
    }
}
