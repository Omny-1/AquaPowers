package dev.bibo.aqua.form;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.anim.TickEffect;
import dev.bibo.aqua.env.Attunement;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Everything a form needs when it fires: the caster, the orb's position, and how much water it holds. */
public final class FormContext {

    /**
     * How full the orb was. This is a <i>mode</i>, not just a number: abilities branch on it and
     * behave differently, so a half-charged spear isn't a worse spear — it's a faster, piercing one.
     * Without this the only correct play was always "fill to 100%, then fire", and there was no
     * decision in the resource at all.
     */
    public enum Charge {
        /** A quick sip. Fast, precise, cheap to re-cast. */
        LIGHT,
        /** The default. Balanced. */
        MEDIUM,
        /** The whole orb. Slow, huge, devastating. */
        HEAVY;

        public boolean isLight() { return this == LIGHT; }
        public boolean isHeavy() { return this == HEAVY; }
    }

    public final AquaWaterPlugin plugin;
    public final Player player;
    public final Location orbCenter;
    /** Number of water blocks collected (orb size). */
    public final int waterAmount;
    /** Raw fill fraction 0..1, before the environment has its say. */
    public final double fill;
    /** Effective charge level — fill scaled by attunement; drives size, damage and aftermath. */
    public final double power;
    /** Which behaviour mode this cast runs in. */
    public final Charge charge;
    /** Where the caster is standing, and what it does to the water. */
    public final Attunement attunement;
    /** Awakened casts get an extra shove on top of everything else. */
    public final boolean awakened;

    public FormContext(AquaWaterPlugin plugin, Player player, Location orbCenter, int waterAmount,
                       Attunement attunement, boolean awakened) {
        this.plugin = plugin;
        this.player = player;
        this.orbCenter = orbCenter;
        this.waterAmount = waterAmount;
        this.attunement = attunement == null ? Attunement.NEUTRAL : attunement;
        this.awakened = awakened;

        this.fill = Math.max(0.0, Math.min(1.0, waterAmount / (double) Math.max(1, plugin.cfg().maxBlocks)));
        double scaled = fill * this.attunement.powerMult * (awakened ? plugin.cfg().awakeningPowerMult : 1.0);
        this.power = Math.max(0.12, Math.min(1.35, scaled));
        this.charge = fill < 0.40 ? Charge.LIGHT : (fill < 0.80 ? Charge.MEDIUM : Charge.HEAVY);
    }

    public void add(TickEffect effect) {
        plugin.animator().add(effect);
    }
}
