package dev.bibo.aqua.command;

import dev.bibo.aqua.AquaWaterPlugin;
import dev.bibo.aqua.form.FormGroup;
import dev.bibo.aqua.form.WaterForm;
import dev.bibo.aqua.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** /aqua command: items, granting powers, listing forms, reload. */
public final class AquaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "give", "catalyst", "grant", "revoke", "on", "off", "awaken", "forms", "debug", "reload", "help");

    private final AquaWaterPlugin plugin;

    public AquaCommand(AquaWaterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String pfx = plugin.cfg().prefix;
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label, pfx);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> {
                if (notAdmin(sender, pfx)) return true;
                Player t = target(sender, args, 1);
                if (t == null) {
                    Msg.send(sender, pfx, "&cSpecify a player.");
                    return true;
                }
                t.getInventory().addItem(plugin.items().holyWater(1));
                Msg.send(sender, pfx, "&bHoly Water &fwas given to &b" + t.getName());
            }
            case "catalyst" -> {
                Player t = target(sender, args, 1);
                if (t == null) {
                    Msg.send(sender, pfx, "&cSpecify a player.");
                    return true;
                }
                boolean self = sender instanceof Player sp && sp.equals(t);
                if (!sender.hasPermission("aquapowers.admin") && !(self && plugin.users().isPowered(t))) {
                    Msg.send(sender, pfx, "&cYou do not have permission.");
                    return true;
                }
                t.getInventory().addItem(plugin.items().catalyst());
                Msg.send(sender, pfx, "&3Water Totem &fwas given to &b" + t.getName());
            }
            case "grant" -> {
                if (notAdmin(sender, pfx)) return true;
                Player t = target(sender, args, 1);
                if (t == null) {
                    Msg.send(sender, pfx, "&cSpecify a player.");
                    return true;
                }
                plugin.users().grant(t, true);
                Msg.send(sender, pfx, "&fWater powers were granted to &b" + t.getName());
            }
            case "revoke" -> {
                if (notAdmin(sender, pfx)) return true;
                Player t = target(sender, args, 1);
                if (t == null) {
                    Msg.send(sender, pfx, "&cSpecify a player.");
                    return true;
                }
                plugin.users().revoke(t);
                Msg.send(sender, pfx, "&fWater powers were revoked from &b" + t.getName());
            }
            case "on" -> {
                if (notAdmin(sender, pfx)) return true;
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, pfx, "&cThis command can only be used by a player.");
                    return true;
                }
                plugin.users().grant(p, true);
            }
            case "off" -> {
                if (notAdmin(sender, pfx)) return true;
                if (!(sender instanceof Player p)) {
                    Msg.send(sender, pfx, "&cThis command can only be used by a player.");
                    return true;
                }
                plugin.users().revoke(p);
            }
            case "awaken" -> {
                if (notAdmin(sender, pfx)) return true;
                Player t = target(sender, args, 1);
                if (t == null) {
                    Msg.send(sender, pfx, "&cSpecify a player.");
                    return true;
                }
                plugin.users().debugAwaken(t);
                Msg.send(sender, pfx, "&bAwakening and full stamina were given to &f" + t.getName());
            }
            case "forms" -> {
                Msg.send(sender, pfx, "&bAbility groups &7(number = group, then number = ability):");
                int gi = 1;
                for (FormGroup g : plugin.forms().groups()) {
                    sender.sendMessage(Msg.color("&8» " + g.color() + "&l" + gi + ". " + g.name()));
                    int si = 1;
                    for (WaterForm f : g.forms()) {
                        sender.sendMessage(Msg.color("   &e" + si + " " + f.name() + " &7— " + f.description()));
                        si++;
                    }
                    gi++;
                }
            }
            case "debug" -> {
                if (notAdmin(sender, pfx)) return true;
                // The plugin's cost is display entities broadcast to nearby clients, which never shows
                // up in a TPS graph or a profiler — it surfaces as rubber-banding on a "healthy"
                // server. This is the only way to see it.
                var an = plugin.animator();
                Msg.send(sender, pfx, "&bStatus &7(tick " + an.ticks() + ")");
                sender.sendMessage(Msg.color("&8Effects: &f" + an.activeCount()
                        + "&8/&7" + plugin.cfg().maxEffects
                        + " &8| displays: &f" + an.totalDisplays()
                        + "&8/&7" + plugin.cfg().maxDisplays));
                var byType = an.breakdown();
                if (byType.isEmpty()) {
                    sender.sendMessage(Msg.color("&8  (empty)"));
                } else {
                    byType.entrySet().stream()
                            .sorted((a, b) -> b.getValue() - a.getValue())
                            .forEach(en -> sender.sendMessage(
                                    Msg.color("&8  " + en.getKey() + " &7× " + en.getValue())));
                }
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    if (!plugin.users().isPowered(pl)) continue;
                    var u = plugin.users().user(pl);
                    sender.sendMessage(Msg.color("&8" + pl.getName()
                            + " &7effects " + an.countFor(pl.getUniqueId())
                            + " &8| &7stamina " + (int) u.getStamina()
                            + " &8| &7attunement " + u.getAttunement().label
                            + " &8| &7orb " + (u.hasOrb() ? u.getOrb().blockCount() : 0)
                            + (u.isAwakening() ? " &c⚡" : "")));
                }
            }
            case "reload" -> {
                if (notAdmin(sender, pfx)) return true;
                plugin.reloadAll();
                if (plugin.cfg().enableRecipes) plugin.items().registerRecipes();
                Msg.send(sender, pfx, "&aConfiguration reloaded.");
            }
            default -> help(sender, label, pfx);
        }
        return true;
    }

    private void help(CommandSender s, String label, String pfx) {
        Msg.send(s, pfx, "&bAquaPowers &7— water manipulation");
        s.sendMessage(Msg.color("&8/" + label + " give [player] &7— give Holy Water"));
        s.sendMessage(Msg.color("&8/" + label + " catalyst [player] &7— give a Water Totem"));
        s.sendMessage(Msg.color("&8/" + label + " grant|revoke <player> &7— grant/revoke powers"));
        s.sendMessage(Msg.color("&8/" + label + " on|off &7— enable/disable your powers"));
        s.sendMessage(Msg.color("&8/" + label + " awaken <player> &7— grant Awakening (testing)"));
        s.sendMessage(Msg.color("&8/" + label + " forms &7— list all abilities"));
        s.sendMessage(Msg.color("&8/" + label + " debug &7— live effects, displays, and player status"));
        s.sendMessage(Msg.color("&8/" + label + " reload &7— reload the configuration"));
        s.sendMessage(Msg.color("&7Controls (empty main hand): &efirst number — group&7, "
                + "&esecond — ability&7, &eF &7— reset."));
    }

    private boolean notAdmin(CommandSender s, String pfx) {
        if (s.hasPermission("aquapowers.admin")) return false;
        Msg.send(s, pfx, "&cYou do not have permission.");
        return true;
    }

    private Player target(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) return Bukkit.getPlayerExact(args[idx]);
        return sender instanceof Player p ? p : null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && Arrays.asList("give", "catalyst", "grant", "revoke", "awaken").contains(args[0].toLowerCase())) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(p.getName());
            }
        }
        return out;
    }
}
