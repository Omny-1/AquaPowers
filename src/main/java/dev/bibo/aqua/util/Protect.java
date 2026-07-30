package dev.bibo.aqua.util;

import dev.bibo.aqua.AquaWaterPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockExplodeEvent;

import java.util.List;

/**
 * The gate every terrain edit passes through.
 *
 * <p>Instead of compiling against WorldGuard, GriefPrevention, Towny, Lands, CoreProtect and friends —
 * a different API each, all optional — we announce the edit as what it physically is: an explosion.
 * A vanilla {@link BlockExplodeEvent} is fired with the exact block list we are about to change.
 * Every land-protection plugin in existence already listens for that event: it either cancels it
 * (nothing happens) or strips the blocks it guards out of {@code blockList()} (we edit only the rest).
 * Logging plugins get a clean audit trail for free, from the same call.
 *
 * <p>So a water meteor respects claims on a server that has claims, and behaves exactly as before on
 * a server that has none — with no dependency, no soft-depend, and no version coupling.
 */
public final class Protect {

    private Protect() {}

    /**
     * @return the sub-list of {@code blocks} the server permits us to change (possibly empty).
     *         The list passed in may be modified in place.
     */
    public static List<Block> filter(AquaWaterPlugin plugin, Location origin, List<Block> blocks) {
        if (!plugin.cfg().respectProtection || blocks.isEmpty()) return blocks;
        Block at = origin.getWorld() == null ? blocks.get(0) : origin.getBlock();
        try {
            BlockExplodeEvent ev = new BlockExplodeEvent(
                    at, at.getState(), blocks, 0.0f, ExplosionResult.DESTROY);
            Bukkit.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) return List.of();
            return ev.blockList();
        } catch (Throwable t) {
            // A broken third-party listener must not take the ability with it.
            plugin.getLogger().warning("Protection check failed, allowing edit: " + t);
            return blocks;
        }
    }
}
