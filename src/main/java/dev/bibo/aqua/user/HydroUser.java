package dev.bibo.aqua.user;

import dev.bibo.aqua.effect.NukeEffect;
import dev.bibo.aqua.env.Attunement;
import dev.bibo.aqua.form.WaterForm;
import dev.bibo.aqua.orb.WaterOrb;
import org.bukkit.boss.BossBar;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player runtime state for a water bender.
 *
 * <p><b>All timing here is in server ticks, taken from {@code Animator.ticks()}, never from
 * {@code System.currentTimeMillis()}.</b> A wall clock is not monotonic — an NTP correction backwards
 * makes "time since last use" negative and jams every cooldown — and it keeps advancing while the
 * server is down, which would clear a five-minute ultimate cooldown across any overnight restart.
 */
public final class HydroUser {

    private final UUID id;

    /** Ability id -> the tick it was last cast on. */
    private final Map<String, Long> formUse = new HashMap<>();

    private WaterOrb orb;
    private WaterForm selected;
    /** -1 = no group chosen yet; the next number press picks one. See {@code form.Selection}. */
    private int currentGroup = -1;
    private long lastHeldTick = Long.MIN_VALUE / 4;

    private double stamina = 100;
    private boolean awakening = false;
    private double awakeningCharge = 0;
    private long lastChargeTick = Long.MIN_VALUE / 4;

    private long lastUseTick = Long.MIN_VALUE / 4;
    private long nukeReadyTick = 0;
    private long lastInteractTick = Long.MIN_VALUE / 4;

    private Attunement attunement = Attunement.NEUTRAL;
    private long lastAttuneTick = Long.MIN_VALUE / 4;

    private BossBar bar;
    private Scoreboard board;
    private Objective hud;
    /** Whatever scoreboard the player had before we took it over, so it can be handed back. */
    private Scoreboard previousBoard;
    private String hudSignature = "";
    private NukeEffect armedNuke;

    public HydroUser(UUID id) {
        this.id = id;
    }

    public UUID id() { return id; }

    public WaterOrb getOrb() { return orb; }
    public void setOrb(WaterOrb orb) { this.orb = orb; }
    public boolean hasOrb() { return orb != null && !orb.isConsumed(); }

    public WaterForm getSelected() { return selected; }
    public void setSelected(WaterForm next) { this.selected = next; }

    /** Guards against the client echo that follows cancelling a held-slot change. */
    public long getLastHeldTick() { return lastHeldTick; }
    public void setLastHeldTick(long v) { this.lastHeldTick = v; }

    public int getCurrentGroup() { return currentGroup; }
    public void setCurrentGroup(int currentGroup) { this.currentGroup = currentGroup; }

    public double getStamina() { return stamina; }
    public void setStamina(double stamina) { this.stamina = stamina; }

    public boolean isAwakening() { return awakening; }
    public void setAwakening(boolean awakening) { this.awakening = awakening; }
    public double getAwakeningCharge() { return awakeningCharge; }
    public void setAwakeningCharge(double v) { this.awakeningCharge = v; }
    public long getLastChargeTick() { return lastChargeTick; }
    public void setLastChargeTick(long t) { this.lastChargeTick = t; }

    public long getLastUseTick() { return lastUseTick; }
    public void setLastUseTick(long v) { this.lastUseTick = v; }

    /** The tick from which the ultimate may be armed again. */
    public long getNukeReadyTick() { return nukeReadyTick; }
    public void setNukeReadyTick(long v) { this.nukeReadyTick = v; }

    public long getLastInteractTick() { return lastInteractTick; }
    public void setLastInteractTick(long v) { this.lastInteractTick = v; }

    public long getFormUse(String id) {
        Long v = formUse.get(id);
        return v == null ? Long.MIN_VALUE / 4 : v;
    }
    public void setFormUse(String id, long tick) { formUse.put(id, tick); }
    public Map<String, Long> formUses() { return formUse; }

    /** Cached environment reading — the scan is expensive, the answer changes slowly. */
    public Attunement getAttunement() { return attunement; }
    public long getLastAttuneTick() { return lastAttuneTick; }
    public void setAttunement(Attunement a, long tick) { this.attunement = a; this.lastAttuneTick = tick; }

    public BossBar getBar() { return bar; }
    public void setBar(BossBar bar) { this.bar = bar; }

    public Scoreboard getBoard() { return board; }
    public void setBoard(Scoreboard board) { this.board = board; }
    public Objective getHud() { return hud; }
    public void setHud(Objective hud) { this.hud = hud; }

    public Scoreboard getPreviousBoard() { return previousBoard; }
    public void setPreviousBoard(Scoreboard s) { this.previousBoard = s; }

    /** Cheap change detector so the sidebar is only rebuilt when it would actually look different. */
    public String getHudSignature() { return hudSignature; }
    public void setHudSignature(String s) { this.hudSignature = s; }

    public NukeEffect getArmedNuke() { return armedNuke; }
    public void setArmedNuke(NukeEffect n) { this.armedNuke = n; }
}
