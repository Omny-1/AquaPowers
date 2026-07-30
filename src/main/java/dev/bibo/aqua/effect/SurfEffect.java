package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.fx.ImpactFx;
import dev.bibo.aqua.orb.WaterBlock;
import dev.bibo.aqua.util.Targeting;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Прибой" — the wave the bender rides instead of throwing.
 *
 * <p>Everything else in the kit sends water away from you. This puts you on top of it: a crest forms
 * under your feet and carries you across the landscape while you steer with the mouse, flattening
 * whatever it runs over. Bail out by sneaking.
 *
 * <p>Riding is done by <b>carrying</b> the player — the crest position is computed first and the
 * player is moved onto it with a velocity that closes the gap, rather than by shoving them with a
 * constant push. A constant push desynchronises badly under latency; closing a gap self-corrects.
 */
public final class SurfEffect extends BaseEffect {

    private final double width;
    private final double height;
    private final double speed;
    private final double dmg;
    private final double push;
    private final int duration;
    private final int cols;
    private final int rows;

    private final Set<java.util.UUID> hitSet = new HashSet<>();
    private Vector dir;
    private Location crest;
    private boolean landed = false;

    public SurfEffect(AquaWaterPlugin plugin, Player player, List<WaterBlock> blocks,
                      double width, double height, double speed, double dmg, double push, int duration) {
        super(plugin, player, blocks);
        this.width = width;
        this.height = height;
        this.speed = speed;
        this.dmg = dmg;
        this.push = push;
        this.duration = duration;
        this.cols = Math.max(2, (int) Math.round(width * 1.4));
        this.rows = Math.max(1, (int) Math.ceil(blocks.size() / (double) cols));

        this.dir = flatLook(player);
        this.crest = Targeting.groundBelow(player.getLocation().add(0, 1.5, 0), 8);

        WorldFx.sound(plugin, player.getLocation(), "minecraft:entity.player.splash.high_speed", 1.3f, 0.6f);
        WorldFx.sound(plugin, player.getLocation(), "minecraft:block.water.ambient", 1.4f, 0.7f);
    }

    private static Vector flatLook(Player p) {
        Vector d = p.getEyeLocation().getDirection();
        d.setY(0);
        if (d.lengthSquared() < 1e-6) d = new Vector(1, 0, 0);
        return d.normalize();
    }

    @Override
    public boolean tick() {
        if (!casterValid()) return true;
        age++;

        // Bail out on sneak — you should always be able to get off the ride.
        if (player.isSneaking() && age > 6) {
            dismount();
            return true;
        }

        // Steer: the crest turns toward where you look, but only so fast. Instant turning would
        // let the wave corner like a car; easing it makes the thing feel like it has mass.
        Vector want = flatLook(player);
        dir = dir.multiply(0.82).add(want.multiply(0.18));
        if (dir.lengthSquared() < 1e-6) dir = want;
        dir.normalize();

        Location ahead = crest.clone().add(dir.clone().multiply(speed));
        Location ground = Targeting.surfaceBelow(ahead.clone().add(0, 3.0, 0), 14);
        if (ground == null) {
            // Off a cliff or over a chasm — the wave sags rather than hanging in the air.
            ground = ahead.clone();
            ground.setY(crest.getY() - 0.45);
        } else if (ground.getY() - crest.getY() > 2.2) {
            dismount();          // refuse to climb sheer walls; gentle slopes are fine
            return true;
        }
        crest = ground;

        renderCrest();
        carry();
        sweep();

        if (age % 3 == 0) {
            WorldFx.splash(plugin, crest.clone().add(0, 0.4, 0), 8);
            WorldFx.shockwave(plugin, crest, width * 0.5, 16);
        }

        if (age > duration) {
            dismount();
            return true;
        }
        return false;
    }

    /** The wave itself: a curling sheet, crest leaning forward, drops stretched along the roll. */
    private void renderCrest() {
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        for (int i = 0; i < blocks.size(); i++) {
            int c = i % cols;
            int r = i / cols;
            double fx = cols <= 1 ? 0 : (c / (double) (cols - 1) - 0.5) * width;
            double fy = (rows <= 1 ? 0.5 : (r / (double) (rows - 1))) * height;
            double bulge = Math.sin((c / (double) cols) * Math.PI) * 0.5;
            double curl = (fy / Math.max(0.1, height)) * 1.1;      // the lip throws forward
            double churn = Math.sin(age * 0.35 + c * 0.6) * 0.18;
            Location pos = crest.clone()
                    .add(perp.clone().multiply(fx))
                    .add(0, fy - 0.6 + churn, 0)
                    .add(dir.clone().multiply(bulge + curl - 1.4));
            WaterBlock b = blocks.get(i);
            b.stretch(dir.clone().setY(0.45), 1.6f, 0.8f);
            b.teleport(pos);
        }
    }

    /** Hold the rider on the crest by closing the gap, not by pushing at a fixed rate. */
    private void carry() {
        Location seat = crest.clone().add(0, height * 0.55, 0);
        Vector delta = seat.toVector().subtract(player.getLocation().toVector());
        if (delta.lengthSquared() > 64) {   // fell too far behind — the wave leaves without you
            dismount();
            return;
        }
        Vector v = delta.multiply(0.55);
        if (v.lengthSquared() > 4.0) v.normalize().multiply(2.0);   // never fling the rider
        v.setY(Math.max(v.getY(), -0.2));
        player.setVelocity(v);
        player.setFallDistance(0);
    }

    /** Anything in front of the wave gets flattened once. */
    private void sweep() {
        Location front = crest.clone().add(dir.clone().multiply(1.2)).add(0, height * 0.4, 0);
        for (LivingEntity le : nearbyLiving(front, Math.max(1.8, width * 0.55), null)) {
            if (hitSet.add(le.getUniqueId())) WorldFx.hurt(plugin, le, dmg, player);
            le.setVelocity(le.getVelocity().add(dir.clone().multiply(push).setY(0.42)));
        }
    }

    private void dismount() {
        if (landed) return;
        landed = true;
        player.setFallDistance(0);
        ImpactFx.WAVE.play(plugin, crest, 0.7);
    }
}
