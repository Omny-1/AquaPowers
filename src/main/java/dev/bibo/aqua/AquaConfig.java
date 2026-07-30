package dev.bibo.aqua;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.Set;

/** Typed, cached view over config.yml. Call {@link #reload(FileConfiguration)} to refresh. */
public final class AquaConfig {

    public String prefix = "&b";

    public int collectRadius = 15;
    public int collectDuration = 40;
    public int maxBlocks = 140;
    public int minBlocks = 6;
    public boolean drainSource = true;
    public boolean restoreSourceOnDisperse = true;

    public BlockData orbBlockData = Material.BLUE_STAINED_GLASS.createBlockData();
    public float orbScale = 0.6f;
    public double orbRadius = 1.7;
    public double heightAboveHead = 1.0;
    public double spinSpeed = 0.06;
    public boolean orbGlow = false;

    public float viewRange = 1.5f;
    public int teleportDuration = 2;
    public int brightness = -1;          // -1 = follow the light where the water actually is

    public int maxEffects = 90;
    public int maxEffectsPerPlayer = 14;
    public int maxDisplays = 1400;
    public int restoreBatch = 120;

    public int cooldownTicks = 10;

    public double staminaMax = 100;
    public int staminaScanRadius = 6;
    public double secWater = 30, secGround = 15, secSand = 10, secLava = 5;

    public boolean awakeningEnabled = true;
    public double awakeningThreshold = 80, awakeningMax = 100, awakeningChargePerDamage = 1.3;
    public double awakeningDecayPerSec = 3, awakeningCostMult = 0.5, awakeningCollectMult = 2.0;
    public double awakeningPowerMult = 1.25;
    public int awakeningGraceTicks = 60;

    public boolean environmentEnabled = true;
    public boolean skyCollection = true;

    public boolean nukeEnabled = true;
    public int nukeRadius = 60, nukeCooldownSeconds = 300, nukeBreakRadius = 22, nukeCapRadius = 15, nukeHeight = 32;
    public double nukeDamage = 220;
    /** Drops used to draw the cloud. The silhouette is only as readable as this number. */
    public int nukeCloudBlocks = 340;
    public float nukeCloudScale = 1.8f;
    /** How many overlapping sub-blasts the crater is built from (0 = one smooth bowl). */
    public int nukeBlastCount = 18;
    public double nukeBlastRoughness = 0.55;

    public boolean damageEnabled = true;
    public boolean friendlyFire = true;
    public double damageMultiplier = 1.0;

    public boolean breakBlocks = true;
    /** With breaking off, may impacts still leave standing water? Off = truly read-only terrain. */
    public boolean floodWithoutBreaking = false;
    public boolean respectProtection = true;
    public boolean permanentBreak = true;
    public int restoreTicks = 1200;
    public int maxBreakRadius = 7;
    public Set<Material> blacklist = EnumSet.noneOf(Material.class);

    public boolean particles = true;
    public boolean sounds = true;
    /** Master scale on every sound the plugin plays — a recording needs headroom for voice. */
    public double soundVolume = 1.0;
    /** Fraction of blast damage that still gets through solid cover (0 = full block). */
    public double coverDamage = 0.45;

    public boolean enableRecipes = true;
    public boolean giveCatalystOnDrink = true;

    public void reload(FileConfiguration c) {
        prefix = c.getString("messages.prefix", "&b");

        collectRadius = clamp(c.getInt("collect.radius", 16), 1, 64);
        collectDuration = Math.max(4, c.getInt("collect.duration-ticks", 40));
        maxBlocks = clamp(c.getInt("collect.max-blocks", 120), 8, 600);
        minBlocks = clamp(c.getInt("collect.min-blocks", 6), 1, maxBlocks);
        drainSource = c.getBoolean("collect.drain-source", true);
        restoreSourceOnDisperse = c.getBoolean("collect.restore-source-on-disperse", true);

        Material m = Material.matchMaterial(c.getString("orb.material", "BLUE_STAINED_GLASS"));
        if (m == null || !m.isBlock()) m = Material.BLUE_STAINED_GLASS;
        try {
            orbBlockData = m.createBlockData();
        } catch (Exception e) {
            orbBlockData = Material.BLUE_STAINED_GLASS.createBlockData();
        }
        orbScale = (float) c.getDouble("orb.block-scale", 0.6);
        orbRadius = c.getDouble("orb.radius", 1.7);
        heightAboveHead = c.getDouble("orb.height-above-head", 1.0);
        spinSpeed = c.getDouble("orb.spin-speed", 0.06);
        orbGlow = c.getBoolean("orb.glow", false);

        viewRange = (float) c.getDouble("display.view-range", 1.5);
        teleportDuration = clamp(c.getInt("display.teleport-duration", 2), 1, 59);
        brightness = clamp(c.getInt("display.brightness", -1), -1, 15);

        maxEffects = clamp(c.getInt("limits.max-effects", 90), 4, 500);
        maxEffectsPerPlayer = clamp(c.getInt("limits.max-effects-per-player", 14), 1, 60);
        maxDisplays = clamp(c.getInt("limits.max-displays", 1400), 100, 20000);
        restoreBatch = clamp(c.getInt("limits.restore-batch", 120), 8, 4000);

        cooldownTicks = Math.max(0, c.getInt("cooldown-ticks", 10));

        staminaMax = c.getDouble("stamina.max", 100);
        staminaScanRadius = clamp(c.getInt("stamina.scan-radius", 6), 1, 12);
        secWater = Math.max(1, c.getDouble("stamina.seconds-near-water", 30));
        secGround = Math.max(1, c.getDouble("stamina.seconds-on-ground", 15));
        secSand = Math.max(1, c.getDouble("stamina.seconds-on-sand", 10));
        secLava = Math.max(1, c.getDouble("stamina.seconds-near-lava", 5));

        awakeningEnabled = c.getBoolean("awakening.enabled", true);
        awakeningThreshold = c.getDouble("awakening.threshold", 80);
        awakeningMax = c.getDouble("awakening.max", 100);
        awakeningChargePerDamage = c.getDouble("awakening.charge-per-damage", 1.3);
        awakeningDecayPerSec = c.getDouble("awakening.decay-per-sec", 3);
        awakeningCostMult = c.getDouble("awakening.cost-multiplier", 0.5);
        awakeningCollectMult = c.getDouble("awakening.collect-speed-mult", 2.0);
        awakeningPowerMult = Math.max(1.0, c.getDouble("awakening.power-multiplier", 1.25));
        awakeningGraceTicks = c.getInt("awakening.grace-ticks", 60);

        environmentEnabled = c.getBoolean("environment.enabled", true);
        skyCollection = c.getBoolean("environment.sky-collection", true);

        nukeEnabled = c.getBoolean("nuke.enabled", true);
        nukeRadius = clamp(c.getInt("nuke.radius", 60), 8, 120);
        nukeCooldownSeconds = Math.max(0, c.getInt("nuke.cooldown-seconds", 300));
        nukeBreakRadius = clamp(c.getInt("nuke.break-radius", 22), 4, 64);
        nukeCapRadius = clamp(c.getInt("nuke.cap-radius", 15), 3, 40);
        nukeHeight = clamp(c.getInt("nuke.height", 32), 8, 80);
        nukeDamage = c.getDouble("nuke.damage", 220);
        nukeCloudBlocks = clamp(c.getInt("nuke.cloud-blocks", 340), 40, 900);
        nukeCloudScale = (float) Math.max(0.2, Math.min(6.0, c.getDouble("nuke.cloud-scale", 1.8)));
        nukeBlastCount = clamp(c.getInt("nuke.blast-count", 18), 0, 80);
        nukeBlastRoughness = Math.max(0.0, Math.min(1.0, c.getDouble("nuke.blast-roughness", 0.55)));

        damageEnabled = c.getBoolean("damage.enabled", true);
        friendlyFire = c.getBoolean("damage.friendly-fire", true);
        damageMultiplier = c.getDouble("damage.global-multiplier", 1.0);

        breakBlocks = c.getBoolean("griefing.break-blocks", true);
        floodWithoutBreaking = c.getBoolean("griefing.flood-without-breaking", false);
        respectProtection = c.getBoolean("griefing.respect-protection", true);
        permanentBreak = c.getBoolean("griefing.permanent", true);
        restoreTicks = Math.max(1, c.getInt("griefing.restore-ticks", 1200));
        maxBreakRadius = clamp(c.getInt("griefing.max-break-radius", 7), 1, 24);

        blacklist = EnumSet.noneOf(Material.class);
        for (String s : c.getStringList("griefing.blacklist")) {
            Material bm = Material.matchMaterial(s);
            if (bm != null) blacklist.add(bm);
        }

        particles = c.getBoolean("effects.particles", true);
        sounds = c.getBoolean("effects.sounds", true);
        soundVolume = Math.max(0.0, Math.min(2.0, c.getDouble("effects.sound-volume", 1.0)));
        coverDamage = Math.max(0.0, Math.min(1.0, c.getDouble("damage.through-cover", 0.45)));

        enableRecipes = c.getBoolean("items.enable-recipes", true);
        giveCatalystOnDrink = c.getBoolean("items.give-catalyst-on-drink", true);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
