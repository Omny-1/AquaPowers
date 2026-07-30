package dev.bibo.aqua.form;

import dev.bibo.aqua.orb.WaterBlock;

import java.util.List;
import java.util.function.BiConsumer;

/** A single ability: name, description, stamina cost, and the behaviour that fires it. */
public final class WaterForm {

    private final String id;
    private final String name;
    private final String description;
    private final double staminaCost;
    private final boolean requiresWater;   // needs a collected orb
    private final boolean awakeningOnly;    // only usable in awakening mode
    private boolean needsEntityTarget = false; // aborts (no cost) if not aimed at a mob/player
    /** Per-ability cooldown in server ticks (0 = only the global anti-spam). Ticks, not millis:
     *  the wall clock is neither monotonic nor paused while the server is down. */
    private long cooldownTicks = 0;
    private final BiConsumer<FormContext, List<WaterBlock>> caster;

    public WaterForm(String id, String name, String description, double staminaCost,
                     boolean requiresWater, boolean awakeningOnly,
                     BiConsumer<FormContext, List<WaterBlock>> caster) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.staminaCost = staminaCost;
        this.requiresWater = requiresWater;
        this.awakeningOnly = awakeningOnly;
        this.caster = caster;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public double staminaCost() { return staminaCost; }
    public boolean requiresWater() { return requiresWater; }
    public boolean awakeningOnly() { return awakeningOnly; }
    public boolean needsEntityTarget() { return needsEntityTarget; }
    public long cooldownTicks() { return cooldownTicks; }

    public WaterForm requireTarget() {
        this.needsEntityTarget = true;
        return this;
    }

    public WaterForm cooldown(double seconds) {
        this.cooldownTicks = Math.round(seconds * 20.0);
        return this;
    }

    public void cast(FormContext ctx, List<WaterBlock> blocks) {
        caster.accept(ctx, blocks);
    }
}
