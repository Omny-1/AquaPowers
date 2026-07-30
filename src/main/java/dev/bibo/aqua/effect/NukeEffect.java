package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.fx.ImpactFx;
import dev.bibo.aqua.orb.WaterCollector;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.Msg;
import dev.bibo.aqua.util.Targeting;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** "Аква-Армагеддон" — gathers all water in a huge radius into a giant orb, then drops it as a
 *  nuclear-style mushroom blast where the caster aims. Two-stage: charge, then {@link #drop}. */
public final class NukeEffect extends BaseEffect {

    private static final double GOLDEN = Math.PI * (3 - Math.sqrt(5));

    private enum Phase { CHARGE, READY, RISE, FALL, MUSHROOM }

    private final List<Location> sources = new ArrayList<>();
    private final List<Vector> sphereOffsets;
    private final double sphereRadius = 5.0;
    private final int chargeTicks;
    private final int breakRadius;
    private final int mushHeight;
    private final int capRadius;
    private final double damage;
    private final int riseTicks = 26, fallTicks = 22, mushTicks = 180;

    private Phase phase = Phase.CHARGE;
    private int phaseAge = 0;
    private Location target, readyCenter, skyPoint;
    private boolean detonated = false;
    private boolean cancelled = false;

    public void cancel() {
        this.cancelled = true;
    }

    public NukeEffect(AquaWaterPlugin plugin, Player player) {
        super(plugin, player, new ArrayList<>());
        this.chargeTicks = (int) Math.max(18, 70 / Math.max(1.0, cfg.awakeningCollectMult));
        this.breakRadius = cfg.nukeBreakRadius;
        this.mushHeight = cfg.nukeHeight;
        this.capRadius = cfg.nukeCapRadius;
        this.damage = cfg.nukeDamage;

        // Borrowed, not taken. Arming costs nothing and can be abandoned (awakening lapses, the
        // caster dies, powers are revoked), and the old code discarded the drain record in a local
        // variable — so arm-and-abandon was a free, repeatable eraser for 300 blocks of water inside
        // a 60-block radius, invisible to logging plugins. Now the water comes back unless the
        // warhead actually detonates.
        List<Block> wanted = new ArrayList<>();
        for (WaterCollector.Drained d : WaterCollector.findSources(player, cfg.nukeRadius, cfg.nukeCloudBlocks)) {
            wanted.add(d.loc().getBlock());
        }
        // `collect.drain-source: false` means "read the water, don't take it" — this path ignored the
        // setting and emptied the map anyway.
        if (cfg.drainSource) borrowAll(player.getLocation(), wanted, Material.AIR);
        // The cloud is drawn with these drops, so its silhouette is only as good as their count.
        // Away from an ocean the old floor of 40 left the mushroom as a handful of specks you could
        // barely make out, which is a poor showing for a five-minute ultimate — the cloud now gets a
        // guaranteed body regardless of how dry the ground was.
        float scale = cfg.orbScale * cfg.nukeCloudScale;
        for (Block b : wanted) {
            Location at = b.getLocation().add(0.5, 0.5, 0.5);
            sources.add(at);
            spawn(at, scale);
        }
        if (blocks.size() < cfg.nukeCloudBlocks) {
            Location c = player.getLocation();
            int need = cfg.nukeCloudBlocks - blocks.size();
            for (int i = 0; i < need; i++) {
                // Condensed out of the air in a shell around the caster, so the charge looks full
                // rather than lopsided toward whatever puddle happened to be nearby.
                double a = GOLDEN * i;
                double rr = 3.0 + rnd(2.5);
                Location at = c.clone().add(Math.cos(a) * rr, 1 + rnd(3), Math.sin(a) * rr);
                sources.add(at);
                spawn(at, scale);
            }
        }
        this.sphereOffsets = Geometry.sphere(blocks.size(), sphereRadius);
        WorldFx.sound(plugin, player.getLocation(), "minecraft:block.beacon.power_select", 1.2f, 0.4f);
    }

    public boolean isReady() {
        return phase == Phase.READY;
    }

    /** Trigger the drop toward the aimed point. */
    public void drop(Location aim) {
        if (phase != Phase.READY) return;
        this.readyCenter = center();
        this.target = Targeting.groundBelow(aim.clone().add(0, 1, 0), 48);
        this.skyPoint = target.clone().add(0, mushHeight + 22, 0);
        this.phase = Phase.RISE;
        this.phaseAge = 0;
        WorldFx.sound(plugin, player.getLocation(), "minecraft:entity.ender_dragon.growl", 1.2f, 0.5f);
    }

    private Location center() {
        return player.getLocation().add(0, player.getHeight() + sphereRadius + cfg.heightAboveHead + 2, 0);
    }

    @Override
    public boolean tick() {
        if (cancelled) return true;
        // A world change mid-charge used to leave the sphere rendering across two worlds.
        if (!casterValid() && phase != Phase.MUSHROOM) return true;
        phaseAge++;
        switch (phase) {
            case CHARGE -> {
                double t = Geometry.easeOut(phaseAge / (double) chargeTicks);
                Location c = center();
                for (int i = 0; i < blocks.size(); i++) {
                    blocks.get(i).teleport(Geometry.lerp(sources.get(i), c.clone().add(sphereOffsets.get(i)), t));
                }
                if (phaseAge % 6 == 0) WorldFx.drip(plugin, c);
                if (phaseAge >= chargeTicks) { phase = Phase.READY; phaseAge = 0; }
            }
            case READY -> {
                Location c = center();
                for (int i = 0; i < blocks.size(); i++) {
                    blocks.get(i).teleport(c.clone().add(Geometry.rotateY(sphereOffsets.get(i), phaseAge * 0.04)));
                }
                if (phaseAge % 6 == 0) WorldFx.drip(plugin, c.clone().add(0, -sphereRadius, 0));
                if (phaseAge % 30 == 0) warnBystanders(false);
                if (phaseAge % 40 == 0) {
                    WorldFx.sound(plugin, c, "minecraft:block.conduit.ambient", 1.6f, 0.4f);
                }
            }
            case RISE -> {
                Location c = Geometry.lerp(readyCenter, skyPoint, Geometry.easeIn(phaseAge / (double) riseTicks));
                renderSphere(c);
                markTargetZone();
                if (phaseAge == 1) warnBystanders(true);
                if (phaseAge >= riseTicks) { phase = Phase.FALL; phaseAge = 0; }
            }
            case FALL -> {
                Location c = Geometry.lerp(skyPoint, target, Geometry.easeIn(phaseAge / (double) fallTicks));
                renderSphere(c);
                markTargetZone();
                WorldFx.trail(plugin, c);
                stretchAll(new Vector(0, -1, 0), 1.0f + phaseAge * 0.07f, 1.0f);
                if (phaseAge % 6 == 0) warnBystanders(true);
                if (phaseAge >= fallTicks) { detonate(); phase = Phase.MUSHROOM; phaseAge = 0; }
            }
            case MUSHROOM -> {
                renderMushroom(phaseAge / (double) mushTicks);
                if (phaseAge % 5 == 0) WorldFx.splash(plugin, target.clone().add(0, mushHeight, 0), 40);
                if (phaseAge > mushTicks) return true;
            }
        }
        return false;
    }

    private void renderSphere(Location c) {
        for (int i = 0; i < blocks.size(); i++) {
            blocks.get(i).teleport(c.clone().add(sphereOffsets.get(i)));
        }
    }

    private void detonate() {
        if (detonated) return;
        detonated = true;
        keepChanges();   // the crater is the point; the water it consumed is spent for real now
        WorldFx.bigCrater(plugin, target, breakRadius, Math.max(6, breakRadius / 2));
        WorldFx.damage(plugin, target, breakRadius * 1.3, damage, player, 1.2, 1.8);
        ImpactFx.NUKE.play(plugin, target, 1.0);
        // Everyone close enough to see it gets the flash and the ringing ears.
        for (Player w : target.getWorld().getPlayers()) {
            double d = w.getLocation().distance(target);
            if (d > cfg.nukeRadius * 2.0) continue;
            w.spawnParticle(Particle.FLASH, w.getEyeLocation().add(w.getLocation().getDirection()), 3);
            if (d < breakRadius * 1.6 && !w.equals(player)) {
                w.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0, false, false));
                w.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0, false, false));
            }
        }
    }

    /**
     * Warn the people who are about to be erased.
     *
     * <p>A 22-block-radius one-shot with no tell is not a fight, it's a coin flip. From the moment the
     * charge is armed everyone nearby hears it building; once it is falling, the target zone is marked
     * on the ground and anyone standing in it is told, in as many words, to run.
     */
    private void warnBystanders(boolean falling) {
        World w = player.getWorld();
        double warnR = cfg.nukeRadius * 1.5;
        for (Player p : w.getPlayers()) {
            if (p.equals(player)) continue;
            double d = p.getLocation().distance(player.getLocation());
            if (d > warnR) continue;
            float pitch = falling ? 1.6f : 0.5f;
            WorldFx.sound(plugin, p.getLocation(), "minecraft:block.beacon.deactivate", 1.0f, pitch);
            if (falling && target != null && p.getLocation().distance(target) < breakRadius * 1.5) {
                p.sendTitle(Msg.color("&4&lБЕГИ"), Msg.color("&cАква-Армагеддон над тобой"), 0, 25, 8);
                WorldFx.sound(plugin, p.getLocation(), "minecraft:entity.wither.spawn", 0.7f, 1.9f);
            } else {
                Msg.actionBar(p, "&4☢ &cАква-Армагеддон заряжается неподалёку...");
            }
        }
    }

    /** Paint the blast footprint on the ground so it can be read and escaped. */
    private void markTargetZone() {
        if (target == null || target.getWorld() == null) return;
        World w = target.getWorld();
        int points = 60;
        for (int i = 0; i < points; i++) {
            double a = (2 * Math.PI * i) / points + phaseAge * 0.03;
            Location p = target.clone().add(Math.cos(a) * breakRadius, 0.3, Math.sin(a) * breakRadius);
            w.spawnParticle(Particle.DUST, p, 1, 0.1, 0.1, 0.1, 0.0,
                    new Particle.DustOptions(Color.fromRGB(220, 40, 30), 3.0f));
        }
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, target.clone().add(0, 1, 0), 8, 0.6, 0.5, 0.6, 0.01);
    }

    /**
     * The mushroom, in three parts instead of one ellipsoid.
     *
     * <p>A flattened sphere on a thin stalk does not read as a mushroom cloud from any distance — it
     * reads as a ball. Real ones have three separate silhouette elements, and each is what makes the
     * shape recognisable: a <b>rolling cap</b> (a torus, not a sphere: the top curls outward and back
     * down on itself), a <b>skirt</b> of condensate hanging under its rim, and a <b>base surge</b>
     * spreading along the ground at the foot of the stalk. All three churn, because a still cloud
     * looks like scenery.
     */
    private void renderMushroom(double t) {
        int n = blocks.size();
        double expand = Geometry.easeOut(Math.min(1, t));
        double churn = t * 26;

        int stemN = Math.max(1, (int) (n * 0.26));
        int surgeN = Math.max(1, (int) (n * 0.14));
        int skirtN = Math.max(1, (int) (n * 0.16));
        int capN = Math.max(1, n - stemN - surgeN - skirtN);

        double capLift = mushHeight + expand * 6 + t * 4;
        double capR = capRadius * (0.7 + 1.7 * expand);      // major radius of the rolling torus
        double tubeR = capR * (0.30 + 0.10 * expand);        // thickness of the roll

        for (int i = 0; i < n; i++) {
            Location at;
            if (i < stemN) {
                // Stalk: narrow waist, flared foot, leaning and twisting as it climbs.
                double f = i / (double) stemN;
                double waist = 1.0 - 0.55 * Math.sin(f * Math.PI);
                double rr = (2.6 + 4.2 * Math.pow(1 - f, 2.2)) * waist;
                double ang = GOLDEN * i + churn * (0.5 + f);
                double lean = Math.sin(f * 2.4 + t * 1.5) * 1.6 * f;
                at = target.clone().add(Math.cos(ang) * rr + lean, f * mushHeight, Math.sin(ang) * rr);
            } else if (i < stemN + surgeN) {
                // Base surge: the dust cloud rolling outward along the ground.
                int j = i - stemN;
                double f = j / (double) surgeN;
                double ang = GOLDEN * j + churn * 0.25;
                double rr = capRadius * (0.5 + 1.9 * expand) * (0.45 + 0.55 * f);
                double lift = 1.2 + Math.sin(f * Math.PI) * 2.6;
                at = target.clone().add(Math.cos(ang) * rr, lift, Math.sin(ang) * rr);
            } else if (i < stemN + surgeN + skirtN) {
                // Skirt: condensate falling away from under the cap's rim.
                int j = i - stemN - surgeN;
                double f = j / (double) skirtN;
                double ang = GOLDEN * j + churn * 0.4;
                double rr = capR * (0.85 + 0.2 * f);
                double drop = -tubeR * (0.6 + 1.9 * f);
                at = target.clone().add(Math.cos(ang) * rr, capLift + drop, Math.sin(ang) * rr);
            } else {
                // Cap: a torus. `u` runs around the ring, `v` around the tube, so the surface curls
                // over the top and tucks back underneath — the outline that says "mushroom cloud".
                int j = i - stemN - surgeN - skirtN;
                double u = GOLDEN * j + churn * 0.18;
                double v = j * 2.399963 + churn * 0.55;
                double wobble = 1.0 + 0.16 * Math.sin(u * 3 + t * 4);
                double ring = capR * wobble + Math.cos(v) * tubeR;
                at = target.clone().add(
                        Math.cos(u) * ring,
                        capLift + Math.sin(v) * tubeR * 0.85,
                        Math.sin(u) * ring);
            }
            blocks.get(i).teleport(at);
        }
        // Everything is boiling upward, so the drops themselves are drawn out on the vertical.
        if (age % 3 == 0) stretchAll(new Vector(0, 1, 0), 1.9f, 1.15f);
    }

    private static double rnd(double mag) {
        return (ThreadLocalRandom.current().nextDouble() * 2 - 1) * mag;
    }
}
