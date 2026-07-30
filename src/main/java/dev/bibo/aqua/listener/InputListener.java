package dev.bibo.aqua.listener;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.form.WaterForm;
import dev.bibo.aqua.user.HydroUser;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Bending controls. Powers only work with the totem in the OFF-HAND ("stance").
 *
 * <p>One rule governs everything here: <b>bending inputs apply only while the main hand is empty.</b>
 * Holding anything at all — sword, pickaxe, a stack of dirt — and the plugin is completely
 * transparent, every key and the wheel do what vanilla does.
 *
 * <ul>
 *   <li><b>Empty hand + scroll / number</b> — the held slot <i>is</i> the ability picker: slot N
 *       selects the Nth ability of the group. Not cancelled, so it can't drop inputs or flicker.</li>
 *   <li><b>Sneak + scroll</b> — step through groups.</li>
 *   <li><b>Sneak + 1…6</b> — jump straight to a group (7–9 stay free for gear).</li>
 *   <li><b>F</b> — swap between the last two abilities you cast; <b>Sneak+F</b> — disperse the orb.</li>
 *   <li><b>RMB</b> gather water, <b>LMB</b> fire.</li>
 * </ul>
 */
public final class InputListener implements Listener {

    private final AquaWaterPlugin plugin;

    public InputListener(AquaWaterPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean stance(Player p) {
        return plugin.users().isPowered(p)
                && plugin.items().isCatalyst(p.getInventory().getItemInOffHand());
    }

    private boolean bendingReady(Player p) {
        if (p.getVehicle() != null) return false;   // no bending from a tank / helicopter / train
        ItemStack m = p.getInventory().getItemInMainHand();
        return m == null || m.getType().isAir();
    }

    /**
     * One physical right-click can raise the interact event twice (main hand and off hand). Ticks are
     * the natural unit for that — the two arrive in the same tick — and it keeps the plugin free of
     * wall-clock timing, which is neither monotonic nor paused while the server is down.
     */
    private boolean throttle(HydroUser u) {
        long now = plugin.animator().ticks();
        if (now - u.getLastInteractTick() < 3) return true;
        u.setLastInteractTick(now);
        return false;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!plugin.items().isHolyWater(e.getItem())) return;
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.users().isPowered(p)) {
                if (!plugin.items().hasCatalystInInventory(p)) {
                    plugin.items().giveCatalyst(p);
                    plugin.users().user(p);
                }
            } else {
                plugin.users().grant(p, plugin.cfg().giveCatalystOnDrink);
            }
        });
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!stance(p)) return;
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            if (!bendingReady(p)) return; // holding an item -> eat / place / use normally
            Block clicked = e.getClickedBlock();
            if (clicked != null && clicked.getType().isInteractable()) return; // open chest / door / etc.
            e.setCancelled(true);
            if (throttle(plugin.users().user(p))) return;
            if (p.isSneaking()) plugin.users().disperse(p);
            else plugin.users().collect(p);
        }
        // LEFT clicks left alone -> block breaking works.
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        if (!stance(p)) return;
        if (!bendingReady(p)) return;
        e.setCancelled(true);
        if (throttle(plugin.users().user(p))) return;
        if (p.isSneaking()) plugin.users().disperse(p);
        else plugin.users().collect(p);
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        if (!stance(p)) return;
        if (!bendingReady(p)) return; // holding a tool -> mine / attack, don't fire
        HydroUser u = plugin.users().user(p);
        WaterForm sel = u.getSelected();
        boolean needsOrb = sel != null && sel.requiresWater() && !sel.id().equals("nuke");
        if (needsOrb && !u.hasOrb()) return;
        plugin.users().fire(p);
    }

    /**
     * Two-press selection: the first number picks a group, the next picks an ability inside it, and
     * F drops back to picking a group. Once a group is chosen, numbers only choose abilities.
     *
     * <p>A slot whose destination holds an item is never claimed — that is the player reaching for
     * their gear, and it is also the only way out of bending mode, so it always wins. Keep the low
     * slots empty and every group stays reachable.
     *
     * <p>Two things this must not repeat. The event is <b>always cancelled when we claim it</b>: the
     * old version had a de-bounce that returned <i>without</i> cancelling, so a fast input slipped
     * through and dragged the held slot onto a real item mid-fight. And the number is read from the
     * destination slot rather than inferred from how far the slot moved — that inference is ambiguous
     * (pressing 4 from slot 3 is a move of exactly one, indistinguishable from a scroll notch) and was
     * the reason the same key used to do different things on different presses.
     */
    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (!stance(p) || !bendingReady(p)) return;

        ItemStack destination = p.getInventory().getItem(e.getNewSlot());
        if (destination != null && !destination.getType().isAir()) return;   // reaching for gear

        e.setCancelled(true);
        HydroUser u = plugin.users().user(p);
        long now = plugin.animator().ticks();
        // Cancelling makes the client resend its slot; ignore the echo, but only AFTER cancelling.
        if (now - u.getLastHeldTick() < 2) return;
        u.setLastHeldTick(now);
        plugin.users().pressNumber(p, e.getNewSlot() + 1);
    }

    /**
     * F clears the selection and puts you back on "choose a group"; Sneak+F disperses the orb.
     *
     * <p>Only while the main hand is empty. Cancelling it unconditionally meant a bender could never
     * swap hands at all — including to move the totem out of the off-hand — so the only way to stop
     * bending was through the inventory screen, which nobody guesses.
     */
    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!stance(p) || !bendingReady(p)) return;
        e.setCancelled(true);
        if (p.isSneaking()) plugin.users().disperse(p);
        else plugin.users().resetSelection(p);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!plugin.items().isCatalyst(e.getItemDrop().getItemStack())) return;
        e.setCancelled(true); // the totem can't be dropped
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        ClickType ct = e.getClick();
        if ((ct == ClickType.DROP || ct == ClickType.CONTROL_DROP) && plugin.items().isCatalyst(e.getCurrentItem())) {
            e.setCancelled(true);
        } else if (e.getSlotType() == InventoryType.SlotType.OUTSIDE && plugin.items().isCatalyst(e.getCursor())) {
            e.setCancelled(true);
        }
    }
}
