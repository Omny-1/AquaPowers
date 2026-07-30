package dev.bibo.aqua.effect;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.util.WorldFx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * "Water Walk" — temporarily freezes a small platform of water under the caster so they can walk
 * on it.
 *
 * <p>The ice now goes through the borrowed-state contract, which fixes two things at once: it is the
 * only remaining ability that changed blocks without asking the server's protection plugins, and its
 * restore used to overwrite whatever was there unconditionally — so a player who built on the frozen
 * platform lost their block when it melted.
 */
public final class WaterWalkEffect extends BaseEffect {

    private final int duration;
    private final int radius;

    public WaterWalkEffect(AquaWaterPlugin plugin, Player player, int duration, int radius) {
        super(plugin, player, new ArrayList<>());
        this.duration = duration;
        this.radius = radius;
        WorldFx.sound(plugin, player.getLocation(), "minecraft:block.glass.place", 1.0f, 1.4f);
    }

    @Override
    public boolean tick() {
        if (!casterValid()) return true;
        age++;

        Location feet = player.getLocation();
        int fx = feet.getBlockX(), fy = feet.getBlockY() - 1, fz = feet.getBlockZ();
        List<Block> wanted = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block b = feet.getWorld().getBlockAt(fx + dx, fy, fz + dz);
                if (b.getType() == Material.WATER) wanted.add(b);
            }
        }
        if (!wanted.isEmpty()) borrowAll(feet, wanted, Material.PACKED_ICE);

        if (age % 6 == 0) WorldFx.trail(plugin, feet);
        return age > duration;
    }
}
