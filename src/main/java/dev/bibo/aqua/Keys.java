package dev.bibo.aqua;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Central holder for the plugin's NamespacedKeys (PDC tags, recipe keys, saved state). */
public final class Keys {

    public final NamespacedKey holyWater;
    public final NamespacedKey catalyst;
    public final NamespacedKey powered;
    public final NamespacedKey holyWaterRecipe;
    public final NamespacedKey catalystRecipe;

    // ---- persisted per-player state -----------------------------------------
    // Stored on the player's own PDC: no files to corrupt, survives world moves, cleaned up with
    // the player. Cooldowns are saved as TICKS REMAINING, never as an absolute timestamp — an
    // absolute one would keep counting down while the server is off and hand everyone a free
    // ultimate after any overnight restart.
    public final NamespacedKey savedStamina;
    public final NamespacedKey savedAwakening;
    public final NamespacedKey savedNukeCd;
    public final NamespacedKey savedFormCds;

    /** Scoreboard tag put on every display entity we spawn, used for orphan cleanup. */
    public static final String DISPLAY_TAG = "aqua_water_fx";

    public Keys(Plugin plugin) {
        this.holyWater = new NamespacedKey(plugin, "holy_water");
        this.catalyst = new NamespacedKey(plugin, "water_catalyst");
        this.powered = new NamespacedKey(plugin, "hydromancer");
        this.holyWaterRecipe = new NamespacedKey(plugin, "holy_water_recipe");
        this.catalystRecipe = new NamespacedKey(plugin, "catalyst_recipe");
        this.savedStamina = new NamespacedKey(plugin, "saved_stamina");
        this.savedAwakening = new NamespacedKey(plugin, "saved_awakening");
        this.savedNukeCd = new NamespacedKey(plugin, "saved_nuke_cd");
        this.savedFormCds = new NamespacedKey(plugin, "saved_form_cds");
    }
}
