package dev.bibo.aqua.listener;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.effect.BarrierEffect;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Disperses orbs on quit / death / world change so no stray water displays are left behind. */
public final class LifecycleListener implements Listener {

    private final AquaWaterPlugin plugin;

    public LifecycleListener(AquaWaterPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.users().ensureOnline(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.users().onQuit(e.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.users().disperse(e.getEntity());
        // A charged ultimate has to go with you. Left in place, the animator reaped the effect while
        // the reference survived, and the next click "fired" a dead object: title, zeroed stamina,
        // five-minute cooldown, no explosion.
        plugin.users().clearArmedNuke(e.getEntity());
        plugin.users().save(e.getEntity());
        // Keep the totem — it doesn't drop on death.
        e.getDrops().removeIf(it -> plugin.items().isCatalyst(it));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.users().isPowered(p) && !plugin.items().hasCatalystInInventory(p)) {
                plugin.items().giveCatalyst(p);
            }
        });
    }

    /**
     * A world change invalidates everything anchored to this player: the effects themselves now bail
     * out via {@code casterValid()}, but the charged ultimate is held by the manager rather than the
     * animator, so it has to be dropped here explicitly.
     */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        plugin.users().disperse(e.getPlayer());
        plugin.users().clearArmedNuke(e.getPlayer());
    }

    /**
     * One handler for every active Water Barrier — the effect keeps a static registry, so N shields
     * cost one map lookup here rather than N registered listeners. Runs at HIGH so other plugins have
     * already had their say about the damage before the water drinks part of it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        BarrierEffect.onDamage(e);
    }
}
