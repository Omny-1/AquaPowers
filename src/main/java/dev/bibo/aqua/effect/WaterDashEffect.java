package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.orb.WaterCollector;
import dev.bibo.aqua.util.Geometry;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** "Водяной Рывок" — gathers water under your feet and launches you toward where you look. */
public final class WaterDashEffect extends BaseEffect {

    private final List<Location> starts = new ArrayList<>();
    private final Location feet;
    private final int gather = 4;
    private final int burst = 8;

    public WaterDashEffect(AquaWaterPlugin plugin, Player player, double power, double upBoost) {
        super(plugin, player, new ArrayList<>());
        this.feet = player.getLocation().clone();

        // Only read the nearby water as spawn points for the burst — the dash is a 12-tick visual,
        // so actually draining it just punched permanent holes in ponds on every jump.
        List<WaterCollector.Drained> drained = WaterCollector.findSources(player, 8, 18);
        for (WaterCollector.Drained d : drained) {
            Location at = d.loc().clone().add(0.5, 0.5, 0.5);
            starts.add(at);
            spawn(at, cfg.orbScale);
        }
        // Conjure a little extra so the burst is visible even with no water around.
        int need = Math.max(0, 18 - starts.size());
        for (int i = 0; i < need; i++) {
            Location at = feet.clone().add((Math.random() * 2 - 1) * 1.5, 0.2, (Math.random() * 2 - 1) * 1.5);
            starts.add(at);
            spawn(at, cfg.orbScale);
        }

        Vector look = player.getEyeLocation().getDirection();
        Vector launch = look.clone().multiply(power);
        launch.setY(Math.max(launch.getY(), 0) + upBoost);
        player.setVelocity(launch);
        player.setFallDistance(0);
        WorldFx.sound(plugin, feet, "minecraft:entity.player.splash.high_speed", 1.0f, 1.2f);
    }

    @Override
    public boolean tick() {
        age++;
        // The splash stays anchored where you jumped, it does NOT follow the player.
        if (age <= gather) {
            double t = Geometry.easeOut(age / (double) gather);
            for (int i = 0; i < blocks.size(); i++) {
                blocks.get(i).teleport(Geometry.lerp(starts.get(i), feet.clone().add(0, 0.3, 0), t));
            }
        } else {
            double t = (age - gather) / (double) burst;
            for (int i = 0; i < blocks.size(); i++) {
                double ang = (Math.PI * (3 - Math.sqrt(5))) * i;
                double r = 0.4 + t * 2.4;
                blocks.get(i).teleport(feet.clone().add(Math.cos(ang) * r, t * 3.4, Math.sin(ang) * r));
            }
        }
        if (age % 3 == 0) WorldFx.trail(plugin, feet.clone().add(0, 0.6, 0));
        return age > gather + burst;
    }
}
