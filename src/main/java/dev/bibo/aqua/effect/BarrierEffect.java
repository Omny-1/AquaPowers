package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Water Barrier" — the kit's only real answer to being hit.
 *
 * <p>Every other ability points outward; this one is the reason a bender can stand in a fight instead
 * of kiting it. A shell of water orbits the caster and drinks incoming damage until its reservoir runs
 * dry, then bursts. Melee attackers get shoved off, and fire is a non-issue while it holds.
 *
 * <p>The damage hook is a static registry rather than a listener per cast: {@link #onDamage} is called
 * once from the plugin's damage listener, so N active barriers cost one map lookup, not N event
 * handlers.
 */
public final class BarrierEffect extends BaseEffect {

    private static final Map<UUID, BarrierEffect> ACTIVE = new ConcurrentHashMap<>();

    /** Feed an incoming hit to the victim's barrier, if they have one. Returns true if it was absorbed. */
    public static boolean onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return false;
        BarrierEffect b = ACTIVE.get(p.getUniqueId());
        return b != null && b.absorb(e);
    }

    public static boolean isActive(UUID id) {
        return ACTIVE.containsKey(id);
    }

    private final int duration;
    private final double radius;
    private final double soak;          // fraction of each hit the water swallows
    private final double push;
    private final List<Vector> shell;
    private double reservoir;           // damage points left before the shell fails
    private final double reservoirMax;
    private double spin = 0;
    private boolean broken = false;

    public BarrierEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                         double radius, int duration, double reservoir, double soak, double push) {
        super(plugin, player, blocks);
        this.radius = radius;
        this.duration = duration;
        this.reservoir = reservoir;
        this.reservoirMax = Math.max(1, reservoir);
        this.soak = Math.max(0.1, Math.min(0.95, soak));
        this.push = push;
        this.shell = Geometry.sphere(blocks.size(), radius);

        BarrierEffect old = ACTIVE.put(player.getUniqueId(), this);
        if (old != null) old.broken = true;   // recasting replaces the old shell rather than stacking

        WorldFx.sound(plugin, player.getLocation(), "minecraft:block.conduit.activate", 1.2f, 0.8f);
        WorldFx.sound(plugin, player.getLocation(), "minecraft:item.trident.return", 0.9f, 0.6f);
    }

    /** Swallow part of a hit. The shell fails once its reservoir is spent. */
    private boolean absorb(EntityDamageEvent e) {
        if (broken) return false;
        // A shield of water has no business saving you from the void or from /kill.
        switch (e.getCause()) {
            case VOID, KILL, CUSTOM, WORLD_BORDER, SUFFOCATION -> {
                return false;
            }
            default -> { }
        }
        // Work off the base damage, not the post-armour figure: mixing the two would subtract a
        // mitigated number from an unmitigated one and over-shield anyone wearing armour.
        double incoming = e.getDamage();
        if (incoming <= 0) return false;

        double taken = Math.min(reservoir, incoming * soak);
        reservoir -= taken;
        e.setDamage(Math.max(0, incoming - taken));

        Location at = player.getLocation().add(0, 1, 0);
        WorldFx.splash(plugin, at, 18);
        WorldFx.sound(plugin, at, "minecraft:entity.player.splash.high_speed", 1.0f, 1.5f);
        if (reservoir <= 0) burst();
        return true;
    }

    private void burst() {
        if (broken) return;
        broken = true;
        Location at = player.getLocation().add(0, 1, 0);
        WorldFx.spray(plugin, at, 60);
        WorldFx.shockwave(plugin, player.getLocation(), radius + 1.2, 26);
        WorldFx.sound(plugin, at, "minecraft:entity.generic.explode", 0.9f, 1.6f);
        // Going out is not free for whoever broke it.
        WorldFx.damage(plugin, at, radius + 1.5, 2.0, player, 0.45, 0.9);
    }

    @Override
    public boolean tick() {
        if (broken || !casterValid()) return true;
        age++;
        spin += 0.22;

        Location c = player.getLocation().add(0, 1.0, 0);
        double health = reservoir / reservoirMax;
        // The shell visibly thins as it is spent — you can see how much protection is left.
        float thin = (float) (0.55 + 0.75 * health);

        boolean reorient = age % 2 == 0;
        for (int i = 0; i < blocks.size(); i++) {
            Vector off = Geometry.rotateY(shell.get(i), spin + (i % 3) * 0.4);
            WaterBlock b = blocks.get(i);
            if (reorient) {
                b.resize(thin);
                b.stretch(Geometry.rotateY(off, Math.PI / 2), 1.7f, 0.7f);
            }
            b.teleport(c.clone().add(off));
        }

        // Water on you is water not burning you.
        if (player.getFireTicks() > 0) {
            player.setFireTicks(0);
            WorldFx.steam(plugin, c, 8);
        }

        // Shove melee attackers out of arm's reach.
        if (age % 4 == 0) {
            for (LivingEntity le : nearbyLiving(c, radius + 0.8, null)) {
                Vector out = le.getLocation().toVector().subtract(c.toVector());
                out.setY(0);
                if (out.lengthSquared() < 1e-4) continue;
                le.setVelocity(le.getVelocity().add(out.normalize().multiply(push).setY(0.22)));
            }
            WorldFx.skin(plugin, c, radius, (int) (4 + 6 * health));
        }

        if (age > duration) {
            WorldFx.splash(plugin, c, 30);
            WorldFx.sound(plugin, c, "minecraft:block.conduit.deactivate", 1.0f, 1.2f);
            return true;
        }
        return false;
    }

    @Override
    public void cleanup() {
        ACTIVE.remove(player.getUniqueId(), this);
        super.cleanup();
    }
}
